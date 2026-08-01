"use client";

import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from "react";
import { getDictation, getLevel, PRIMARY_LEVELS, splitTextIntoFragments, type PrimaryLevel } from "./lib/domain";
import {
  MediaPipeAttentionDetector,
  type AttentionDetector,
  type AttentionState,
} from "./lib/vision";

type Screen = "setup" | "placement" | "calibration-screen" | "session" | "summary";
type SessionPhase = "memorizing" | "decision";
type CalibrationPhase = "preparing" | "measuring" | "ready" | "failed";

const CALIBRATION_PREPARATION_MS = 2000;
const CALIBRATION_MEASUREMENT_MS = 1500;

const INITIAL_LEVEL: PrimaryLevel = "CP";
const INITIAL_DICTATION = getDictation(INITIAL_LEVEL, 0);
const INITIAL_CURSORS: Record<PrimaryLevel, number> = {
  CP: 1,
  CE1: 0,
  CE2: 0,
  CM1: 0,
  CM2: 0,
};

export function DictaApp() {
  const [screen, setScreen] = useState<Screen>("setup");
  const [selectedLevel, setSelectedLevel] = useState<PrimaryLevel>(INITIAL_LEVEL);
  const [selectedDictation, setSelectedDictation] = useState(INITIAL_DICTATION);
  const [text, setText] = useState(INITIAL_DICTATION.text);
  const [lettersPerFragment, setLettersPerFragment] = useState(getLevel(INITIAL_LEVEL).recommendedLetters);
  const [cameraMode, setCameraMode] = useState<"camera" | "manual">("camera");
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [cameraLoading, setCameraLoading] = useState(false);
  const [attention, setAttention] = useState<AttentionState>("unknown");
  const [faceDetected, setFaceDetected] = useState(false);
  const [calibrationPhase, setCalibrationPhase] = useState<CalibrationPhase>("preparing");
  const [calibrationAttempt, setCalibrationAttempt] = useState(0);
  const [phase, setPhase] = useState<SessionPhase>("memorizing");
  const [fragmentIndex, setFragmentIndex] = useState(0);
  const [reviewCounts, setReviewCounts] = useState<number[]>([]);
  const [toast, setToast] = useState<string | null>(null);
  const detectorRef = useRef<AttentionDetector | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const screenRef = useRef(screen);
  const phaseRef = useRef(phase);
  const cameraModeRef = useRef(cameraMode);
  const lastCameraReadingAtRef = useRef(0);
  const dictationCursorsRef = useRef(INITIAL_CURSORS);

  useEffect(() => {
    screenRef.current = screen;
    phaseRef.current = phase;
    cameraModeRef.current = cameraMode;
  }, [cameraMode, phase, screen]);

  const fragments = useMemo(() => splitTextIntoFragments(text, {
    targetLetters: lettersPerFragment,
  }), [lettersPerFragment, text]);
  const totalReviews = reviewCounts.reduce((sum, value) => sum + value, 0);
  const keepCameraMounted = cameraMode === "camera" && screen !== "setup" && screen !== "summary";

  const selectLevel = (event: ChangeEvent<HTMLSelectElement>) => {
    const level = event.target.value as PrimaryLevel;
    const nextDictation = getDictation(level, dictationCursorsRef.current[level]);
    dictationCursorsRef.current[level] = (nextDictation.index + 1) % nextDictation.total;
    setSelectedLevel(level);
    setSelectedDictation(nextDictation);
    setText(nextDictation.text);
    setLettersPerFragment(getLevel(level).recommendedLetters);
  };

  const setLetterTarget = (value: number) => {
    if (!Number.isFinite(value)) return;
    setLettersPerFragment(Math.min(100, Math.max(1, Math.round(value))));
  };

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 2600);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const stopCamera = useCallback(() => {
    detectorRef.current?.stop();
    detectorRef.current = null;
    setAttention("unknown");
    setFaceDetected(false);
  }, []);

  useEffect(() => stopCamera, [stopCamera]);

  const primeAudioFeedback = useCallback(() => {
    if (typeof window === "undefined") return null;
    const AudioContextConstructor = window.AudioContext ?? (window as typeof window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextConstructor) return null;
    try {
      const context = audioContextRef.current ?? new AudioContextConstructor();
      audioContextRef.current = context;
      void context.resume().catch(() => undefined);
      return context;
    } catch {
      return null;
    }
  }, []);

  const playCalibrationBeep = useCallback(() => {
    const context = primeAudioFeedback();
    if (!context) return;
    const emit = () => {
      const now = context.currentTime;
      const oscillator = context.createOscillator();
      const gain = context.createGain();
      oscillator.type = "sine";
      oscillator.frequency.setValueAtTime(880, now);
      gain.gain.setValueAtTime(0.0001, now);
      gain.gain.exponentialRampToValueAtTime(0.18, now + 0.015);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.22);
      oscillator.connect(gain).connect(context.destination);
      oscillator.start(now);
      oscillator.stop(now + 0.22);
    };
    if (context.state === "suspended") void context.resume().then(emit).catch(() => undefined);
    else emit();
  }, [primeAudioFeedback]);

  useEffect(() => () => {
    void audioContextRef.current?.close();
    audioContextRef.current = null;
  }, []);

  const startCamera = useCallback(() => {
    primeAudioFeedback();
    setCameraError(null);
    setCameraLoading(true);
    setCameraMode("camera");
    setScreen("placement");
  }, [primeAudioFeedback]);

  useEffect(() => {
    if (screen !== "placement" || !cameraLoading || detectorRef.current) return;
    let cancelled = false;
    const detector = new MediaPipeAttentionDetector({
      analysisFps: 10,
      enterNotebookMs: 220,
      returnScreenMs: 600,
      wasmPath: "/mediapipe/wasm",
      modelAssetPath: "/models/face_landmarker.task",
    });
    detectorRef.current = detector;
    detector.subscribe((reading) => {
      lastCameraReadingAtRef.current = Date.now();
      setAttention(reading.state);
      setFaceDetected(reading.faceDetected);
      if (screenRef.current !== "session" || cameraModeRef.current !== "camera") return;
      // Missing face is published as "notebook" by the detector. Never gate
      // this transition on a previous screen reading: leaving the frame must
      // hide the text immediately.
      if (phaseRef.current === "memorizing" && (!reading.faceDetected || reading.state === "notebook")) setPhase("decision");
    });

    const openCamera = async () => {
      try {
        if (!videoRef.current) throw new Error("The camera preview is not ready.");
        await detector.start(videoRef.current);
        if (cancelled) detector.stop();
      } catch {
        detector.stop();
        if (cancelled) return;
        detectorRef.current = null;
        setCameraError("La caméra n’est pas disponible. Vous pouvez continuer en mode manuel.");
        setCameraLoading(false);
        setScreen("setup");
      } finally {
        if (!cancelled) setCameraLoading(false);
      }
    };

    void openCamera();
    return () => { cancelled = true; };
  }, [cameraLoading, screen]);

  useEffect(() => {
    if (screen !== "session" || cameraMode !== "camera" || phase !== "memorizing") return;
    const watchdog = window.setInterval(() => {
      // If MediaPipe or the video loop stops answering, fail closed instead of
      // preserving the last optimistic "screen" state forever.
      if (Date.now() - lastCameraReadingAtRef.current > 1200) setPhase("decision");
    }, 250);
    return () => window.clearInterval(watchdog);
  }, [cameraMode, phase, screen]);

  useEffect(() => {
    if (cameraMode !== "camera" || !detectorRef.current) return;
    if (screen !== "calibration-screen") return;
    let measurementTimer: number | undefined;
    const finishMeasurement = () => {
      try {
        // Stop collecting at the exact end of the measurement. Previously this
        // only happened after a click, polluting the screen reference with all
        // the movements made between calibration steps.
        detectorRef.current?.finishCalibration("screen");
        setCalibrationPhase("ready");
        playCalibrationBeep();
        setFragmentIndex(0);
        setReviewCounts(Array(fragments.length).fill(0));
        setPhase("memorizing");
        setScreen("session");
      } catch {
        setCalibrationPhase("failed");
        setToast("Je n’ai pas assez vu ton visage. Replace-toi puis réessaie.");
      }
    };
    const preparationTimer = window.setTimeout(() => {
      setCalibrationPhase("measuring");
      measurementTimer = window.setTimeout(finishMeasurement, CALIBRATION_MEASUREMENT_MS);
    }, CALIBRATION_PREPARATION_MS);
    return () => {
      window.clearTimeout(preparationTimer);
      if (measurementTimer !== undefined) window.clearTimeout(measurementTimer);
    };
  }, [calibrationAttempt, cameraMode, fragments.length, playCalibrationBeep, screen]);

  const beginManual = () => {
    stopCamera();
    setCameraLoading(false);
    setCameraMode("manual");
    setCameraError(null);
    setScreen("session");
    setPhase("memorizing");
    setFragmentIndex(0);
    setReviewCounts(Array(fragments.length).fill(0));
  };

  const hideFragment = () => setPhase("decision");

  const review = () => {
    setReviewCounts((counts) => counts.map((count, index) => index === fragmentIndex ? count + 1 : count));
    setPhase("memorizing");
  };

  const next = () => {
    if (fragmentIndex >= fragments.length - 1) {
      stopCamera();
      setScreen("summary");
      return;
    }
    setFragmentIndex((value) => value + 1);
    setPhase("memorizing");
  };

  const reset = () => {
    stopCamera();
    setCameraLoading(false);
    setScreen("setup");
    setPhase("memorizing");
    setFragmentIndex(0);
  };

  return (
    <main className="app-shell">
      <header className="topbar">
        <div className="brand"><span className="brand-mark">D</span>Dicta</div>
        {screen !== "setup" && <button className="icon-button" aria-label="Quitter la séance" onClick={reset}>×</button>}
      </header>

      {screen === "setup" && (
        <>
          <section className="hero">
            <div className="eyebrow">Dictée de mémoire</div>
            <h1>Je regarde.<br />J’écris. <em>Je retiens.</em></h1>
            <p>Quelques mots apparaissent, puis disparaissent quand les yeux se tournent vers le cahier.</p>
          </section>
          <section className="card setup-card">
            <label className="field-label" htmlFor="level-select">Choisir le niveau <span>{selectedDictation.index + 1} / {selectedDictation.total}</span></label>
            <select id="level-select" className="level-select" value="" onChange={selectLevel}>
              <option value="" disabled>Choisir une classe…</option>
              {PRIMARY_LEVELS.map((level) => <option key={level.id} value={level.id}>{level.label}</option>)}
            </select>
            <div className="dictation-meta">
              <strong>{getLevel(selectedLevel).label}</strong>
              <span>Dictée {selectedDictation.index + 1} sur {selectedDictation.total} · {getLevel(selectedLevel).cycle} · {text.trim().split(/\s+/).filter(Boolean).length} mots</span>
            </div>
            <div className="settings-row">
              <div><strong>Lettres par étape</strong><div className="muted">Arrondi au mot supérieur · Environ {fragments.length} fragments</div></div>
              <div className="stepper">
                <button aria-label="Réduire le nombre de lettres" onClick={() => setLetterTarget(lettersPerFragment - 1)}>−</button>
                <input
                  className="letters-input"
                  type="number"
                  inputMode="numeric"
                  min="1"
                  max="100"
                  step="1"
                  aria-label="Nombre de lettres par étape"
                  value={lettersPerFragment}
                  onChange={(event) => setLetterTarget(Number(event.target.value))}
                />
                <button aria-label="Augmenter le nombre de lettres" onClick={() => setLetterTarget(lettersPerFragment + 1)}>+</button>
              </div>
            </div>
            <button className="primary-button" disabled={!text.trim() || cameraLoading} onClick={startCamera}>{cameraLoading ? "Préparation de la caméra…" : "Préparer la caméra"}</button>
            {cameraError && <p role="alert" className="muted">{cameraError}</p>}
            <button className="manual-hide" onClick={beginManual}>Continuer sans caméra</button>
          </section>
          <p className="privacy-note"><span className="privacy-dot" />La vidéo reste sur ce téléphone. Aucune image n’est enregistrée ni envoyée.</p>
        </>
      )}

      {keepCameraMounted && (
        <section
          className={`session-shell placement-shell ${screen === "placement" ? "" : "camera-keeper"}`}
          aria-hidden={screen !== "placement"}
          inert={screen !== "placement"}
        >
          <div className="hero"><div className="eyebrow">Installation</div><h1>Place ton visage dans le repère.</h1><p>Pose le téléphone verticalement, à peu près à la longueur d’un bras.</p></div>
          <div className="card setup-card placement-card">
            <div className="camera-stage" data-ready={faceDetected ? "true" : "false"}>
              <video ref={videoRef} className="camera-feed" autoPlay muted playsInline aria-label="Retour de la caméra" />
              <div className="camera-shade" aria-hidden="true" />
              <div className="face-guide" aria-hidden="true">
                <span className="guide-corner top-left" />
                <span className="guide-corner top-right" />
                <span className="guide-corner bottom-left" />
                <span className="guide-corner bottom-right" />
              </div>
              <div className="camera-status" aria-live="polite">
                <span className="camera-status-dot" />
                {cameraLoading ? "Ouverture de la caméra…" : faceDetected ? "Visage détecté" : "Place ton visage dans le cadre"}
              </div>
            </div>
            <div className="placement-tip"><span className="placement-tip-icon" aria-hidden="true">◎</span><span>Centre ton visage dans le cadre, puis garde le téléphone bien droit.</span></div>
            <button className="primary-button" disabled={!faceDetected || cameraLoading} onClick={() => { primeAudioFeedback(); detectorRef.current?.beginCalibration("screen"); setCalibrationPhase("preparing"); setScreen("calibration-screen"); }}>{faceDetected ? "Mon visage est bien placé" : "Recherche du visage…"}</button>
            <button className="manual-hide" onClick={beginManual}>Utiliser le mode manuel</button>
          </div>
        </section>
      )}

      {screen === "calibration-screen" && (
        <section className="session-shell calibration-shell">
          <div className="card stage-card calibration-card" data-phase={calibrationPhase}>
            {calibrationPhase === "failed" ? (
              <>
                <div className="calibration-message" role="alert">Replace ton visage dans le champ de la caméra.</div>
                <button className="primary-button" onClick={() => { detectorRef.current?.beginCalibration("screen"); setCalibrationPhase("preparing"); setCalibrationAttempt((value) => value + 1); }}>Réessayer</button>
              </>
            ) : (
              <>
                <div className="calibration-message" role="status" aria-live="polite">Regardez la caméra</div>
                <span className="sr-only">{calibrationPhase === "preparing" ? "La calibration commence dans deux secondes." : "Calibration en cours."}</span>
              </>
            )}
          </div>
        </section>
      )}

      {screen === "session" && fragments.length > 0 && (
        <section className="session-shell">
          <div className="progress-wrap">
            <div className="progress-label"><span>Étape {fragmentIndex + 1} sur {fragments.length}</span><span>{Math.round(((fragmentIndex + 1) / fragments.length) * 100)} %</span></div>
            <div className="progress-track"><div className="progress-bar" style={{ width: `${((fragmentIndex + 1) / fragments.length) * 100}%` }} /></div>
          </div>
          <div className={`card stage-card ${phase === "memorizing" ? "gaze-target-card" : ""}`}>
            {phase === "memorizing" && (
              <>
                <div className="status-pill" data-tone={attention === "unknown" ? "unknown" : undefined}><span className="pulse-dot" />{cameraMode === "manual" ? "Mode manuel" : attention === "screen" ? "Regard détecté" : "Analyse du regard"}</div>
                <div className="fragment">{fragments[fragmentIndex]}</div>
                <p className="stage-help">Mémorise ces mots, puis écris-les sur ton cahier.</p>
                <button className="manual-hide" onClick={hideFragment}>{cameraMode === "camera" ? "Masquer maintenant" : "J’ai mémorisé"}</button>
              </>
            )}
            {phase === "decision" && (
              <>
                <div className="eyebrow">À toi de choisir</div>
                <div className="fragment" style={{ fontSize: "clamp(30px, 8vw, 46px)" }}>Tu veux revoir les mots ?</div>
                <div className="choice-grid">
                  <button className="choice-button review" onClick={review}>↶ Revoir</button>
                  <button className="choice-button next" onClick={next}>Continuer →</button>
                </div>
              </>
            )}
          </div>
        </section>
      )}

      {screen === "summary" && (
        <section className="session-shell">
          <div className="hero"><div className="eyebrow">Dictée terminée</div><h1>Bravo, c’est terminé !</h1><p>Chaque relecture aide à mieux connaître sa mémoire.</p></div>
          <div className="card stage-card">
            <div className="summary-number">{fragments.length}</div><div className="muted">fragments écrits</div>
            <div className="summary-grid">
              <div className="summary-stat"><strong>{totalReviews}</strong><span>relectures</span></div>
              <div className="summary-stat"><strong>{reviewCounts.filter(Boolean).length}</strong><span>fragments revus</span></div>
            </div>
            <button className="primary-button" onClick={reset}>Préparer une autre dictée</button>
          </div>
        </section>
      )}

      {toast && <div className="toast" role="status">{toast}</div>}
    </main>
  );
}
