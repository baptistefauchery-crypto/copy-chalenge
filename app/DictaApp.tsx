"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { splitTextIntoFragments } from "./lib/domain";
import {
  MediaPipeAttentionDetector,
  type AttentionDetector,
  type AttentionState,
} from "./lib/vision";

type Screen = "setup" | "placement" | "calibration-screen" | "calibration-notebook" | "dictation-ready" | "session" | "summary";
type SessionPhase = "memorizing" | "writing" | "decision";
type CalibrationPhase = "preparing" | "measuring" | "ready";

const DEMO_TEXT = "Le petit renard traverse le jardin. Il s’arrête près des fleurs, puis écoute le vent dans les arbres.";
const CALIBRATION_PREPARATION_MS = { screen: 2000, notebook: 5000 } as const;
const CALIBRATION_MEASUREMENT_MS = 3000;

export function DictaApp() {
  const [screen, setScreen] = useState<Screen>("setup");
  const [text, setText] = useState(DEMO_TEXT);
  const [wordsPerFragment, setWordsPerFragment] = useState(5);
  const [cameraMode, setCameraMode] = useState<"camera" | "manual">("camera");
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [cameraLoading, setCameraLoading] = useState(false);
  const [attention, setAttention] = useState<AttentionState>("unknown");
  const [faceDetected, setFaceDetected] = useState(false);
  const [calibrationPhase, setCalibrationPhase] = useState<CalibrationPhase>("preparing");
  const [phase, setPhase] = useState<SessionPhase>("memorizing");
  const [fragmentIndex, setFragmentIndex] = useState(0);
  const [reviewCounts, setReviewCounts] = useState<number[]>([]);
  const [toast, setToast] = useState<string | null>(null);
  const [calibrationSuccess, setCalibrationSuccess] = useState(false);
  const detectorRef = useRef<AttentionDetector | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const screenRef = useRef(screen);
  const phaseRef = useRef(phase);
  const cameraModeRef = useRef(cameraMode);

  useEffect(() => {
    screenRef.current = screen;
    phaseRef.current = phase;
    cameraModeRef.current = cameraMode;
  }, [cameraMode, phase, screen]);

  const fragments = useMemo(() => splitTextIntoFragments(text, {
    targetWords: wordsPerFragment,
    minWords: Math.min(3, wordsPerFragment),
    maxWords: Math.min(10, wordsPerFragment + 2),
  }), [text, wordsPerFragment]);
  const totalReviews = reviewCounts.reduce((sum, value) => sum + value, 0);

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
      enterNotebookMs: 300,
      returnScreenMs: 500,
      wasmPath: "/mediapipe/wasm",
      modelAssetPath: "/models/face_landmarker.task",
    });
    detectorRef.current = detector;
    detector.subscribe((reading) => {
      setAttention(reading.state);
      setFaceDetected(reading.faceDetected);
      if (screenRef.current !== "session" || cameraModeRef.current !== "camera") return;
      if (phaseRef.current === "memorizing" && reading.state === "notebook") setPhase("writing");
      if (phaseRef.current === "writing" && reading.state === "screen") setPhase("decision");
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
    if (cameraMode !== "camera" || !detectorRef.current) return;
    if (screen !== "calibration-screen" && screen !== "calibration-notebook") return;
    const target = screen === "calibration-screen" ? "screen" : "notebook";
    let measurementTimer: number | undefined;
    let feedbackTimer: number | undefined;
    const finishMeasurement = () => {
      setCalibrationPhase("ready");
      if (target !== "notebook") return;
      setCalibrationSuccess(true);
      playCalibrationBeep();
      feedbackTimer = window.setTimeout(() => setCalibrationSuccess(false), 500);
    };
    const preparationTimer = window.setTimeout(() => {
      detectorRef.current?.beginCalibration(target);
      setCalibrationPhase("measuring");
      measurementTimer = window.setTimeout(finishMeasurement, CALIBRATION_MEASUREMENT_MS);
    }, CALIBRATION_PREPARATION_MS[target]);
    return () => {
      window.clearTimeout(preparationTimer);
      if (measurementTimer !== undefined) window.clearTimeout(measurementTimer);
      if (feedbackTimer !== undefined) window.clearTimeout(feedbackTimer);
    };
  }, [cameraMode, playCalibrationBeep, screen]);

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

  const runCalibrationStep = (next: Screen) => {
    const target = screen === "calibration-screen" ? "screen" : "notebook";
    try {
      detectorRef.current?.finishCalibration(target);
    } catch {
      setToast("Reste bien en place pendant toute la mesure");
      return false;
    }
    if (target === "notebook") {
      const calibration = detectorRef.current?.getCalibration();
      if (!calibration) {
        setCalibrationPhase("preparing");
        setToast("La mesure n’a pas pu être enregistrée. Recommençons.");
        setScreen("placement");
        return false;
      }
    }
    setToast("Mesure enregistrée");
    setCalibrationPhase("preparing");
    window.setTimeout(() => setScreen(next), 450);
    return true;
  };

  const startSession = () => {
    setFragmentIndex(0);
    setReviewCounts(Array(fragments.length).fill(0));
    setPhase("memorizing");
    setScreen("session");
  };

  const hideFragment = () => setPhase("writing");
  const showDecision = () => setPhase("decision");

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
            <label className="field-label" htmlFor="dictation">Texte de la dictée <span>{text.trim().split(/\s+/).filter(Boolean).length} mots</span></label>
            <textarea id="dictation" className="text-input" value={text} onChange={(event) => setText(event.target.value)} />
            <div className="settings-row">
              <div><strong>Mots par étape</strong><div className="muted">Environ {fragments.length} fragments</div></div>
              <div className="stepper">
                <button aria-label="Réduire" onClick={() => setWordsPerFragment((value) => Math.max(2, value - 1))}>−</button>
                <strong>{wordsPerFragment}</strong>
                <button aria-label="Augmenter" onClick={() => setWordsPerFragment((value) => Math.min(9, value + 1))}>+</button>
              </div>
            </div>
            <button className="primary-button" disabled={!text.trim() || cameraLoading} onClick={startCamera}>{cameraLoading ? "Préparation de la caméra…" : "Préparer la caméra"}</button>
            {cameraError && <p role="alert" className="muted">{cameraError}</p>}
            <button className="manual-hide" onClick={beginManual}>Continuer sans caméra</button>
          </section>
          <p className="privacy-note"><span className="privacy-dot" />La vidéo reste sur ce téléphone. Aucune image n’est enregistrée ni envoyée.</p>
        </>
      )}

      {screen === "placement" && (
        <section className="session-shell placement-shell">
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
            <button className="primary-button" disabled={!faceDetected || cameraLoading} onClick={() => { primeAudioFeedback(); setCalibrationPhase("preparing"); setScreen("calibration-screen"); }}>{faceDetected ? "Mon visage est bien placé" : "Recherche du visage…"}</button>
            <button className="manual-hide" onClick={beginManual}>Utiliser le mode manuel</button>
          </div>
        </section>
      )}

      {screen === "calibration-screen" && (
        <section className="session-shell calibration-shell">
          <div className="hero"><div className="eyebrow">Calibration · 1 sur 2</div><h1>Regarde le point.</h1><p>Garde la tête tranquille et regarde le centre de l’écran pendant quelques secondes.</p></div>
          <div className="card stage-card calibration-card">
            <div className="summary-number">●</div>
            <p className="stage-help calibration-help">
              {calibrationPhase === "preparing" ? "Regarde le point. La mesure va commencer dans un instant." : calibrationPhase === "measuring" ? "Garde les yeux sur le point encore un instant." : "La mesure est terminée."}
            </p>
            <button className="primary-button" disabled={calibrationPhase !== "ready"} onClick={() => runCalibrationStep("calibration-notebook")}>
              {calibrationPhase === "ready" ? "Continuer" : calibrationPhase === "preparing" ? "Prépare-toi…" : "Mesure en cours…"}
            </button>
          </div>
        </section>
      )}

      {screen === "calibration-notebook" && (
        <section className="session-shell">
          <div className="hero"><div className="eyebrow">Calibration · 2 sur 2</div><h1>Regarde ton cahier.</h1><p>Baisse les yeux vers l’endroit où tu vas écrire, sans déplacer le téléphone.</p></div>
          <div className="card stage-card calibration-card">
            <div className="summary-number">↓</div>
            <p className="stage-help calibration-help">
              {calibrationPhase === "preparing" ? "Baisse les yeux maintenant. La mesure commencera dans quelques secondes." : calibrationPhase === "measuring" ? "Garde les yeux sur ton cahier encore un instant." : "La mesure est terminée."}
            </p>
            <button className="primary-button" disabled={calibrationPhase !== "ready"} onClick={() => runCalibrationStep("dictation-ready")}>
              {calibrationPhase === "ready" ? "Terminer la calibration" : calibrationPhase === "preparing" ? "Prépare-toi…" : "Mesure en cours…"}
            </button>
          </div>
        </section>
      )}

      {screen === "dictation-ready" && (
        <section className="session-shell ready-shell">
          <div className="hero"><div className="eyebrow">Calibration terminée</div><h1>Tout est prêt.</h1><p>Ton regard est bien calibré. Tu peux maintenant passer à la première étape de la dictée.</p></div>
          <div className="card stage-card ready-card">
            <div className="summary-number">✓</div>
            <p className="stage-help ready-help">Les mots vont apparaître à l’écran. Mémorise-les, puis regarde ton cahier pour les écrire.</p>
            <button className="primary-button" onClick={startSession}>Commencer la dictée</button>
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
                <p className="stage-help">Mémorise ces mots, puis regarde ton cahier.</p>
                <button className="manual-hide" onClick={hideFragment}>{cameraMode === "camera" ? "Masquer maintenant" : "J’ai mémorisé"}</button>
              </>
            )}
            {phase === "writing" && (
              <>
                <div className="status-pill" data-tone="writing"><span className="pulse-dot" />Texte caché</div>
                <div className="hidden-fragment" aria-label="Texte masqué" />
                <p className="stage-help">Écris les mots sur ton cahier. Relève les yeux quand tu as terminé.</p>
                <button className="primary-button" onClick={showDecision}>J’ai relevé les yeux</button>
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

      {calibrationSuccess && (
        <div className="calibration-success" role="status" aria-live="assertive">
          <span className="calibration-success-mark" aria-hidden="true">✓</span>
          <span>Tu peux relever les yeux</span>
        </div>
      )}
      {toast && <div className="toast" role="status">{toast}</div>}
    </main>
  );
}
