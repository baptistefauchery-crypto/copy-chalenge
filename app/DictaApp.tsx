"use client";

import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore, type CSSProperties, type ChangeEvent } from "react";
import {
  calculateScore,
  getDictation,
  getLevel,
  LEADERBOARD_STORAGE_KEY,
  PRIMARY_LEVELS,
  sortLeaderboard,
  splitTextIntoFragments,
  type LeaderboardEntry,
  type PrimaryLevel,
} from "./lib/domain";
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
const AUTO_HIDE_GRACE_MS = 2000;
const CONFETTI_COLORS = ["#ef765f", "#6654d9", "#58a37c", "#f3b34f"] as const;
const CONFETTI_PIECES = Array.from({ length: 56 }, (_, index) => {
  const origin = index % 6 === 0 ? "left" : index % 6 === 1 ? "right" : "top";
  return {
    origin,
    left: origin === "left" ? "-12px" : origin === "right" ? "calc(100% + 12px)" : `${5 + ((index * 19) % 90)}%`,
    top: origin === "top" ? "-18px" : `${18 + ((index * 23) % 58)}%`,
    delay: `${(index % 16) * 240 + Math.floor(index / 16) * 180}ms`,
    drift: `${((index * 31) % 70) - 35}px`,
    rotate: `${((index * 47) % 60) - 30}deg`,
    color: CONFETTI_COLORS[index % CONFETTI_COLORS.length],
  };
});

const INITIAL_LEVEL: PrimaryLevel = "CP";
const INITIAL_CURSORS: Record<PrimaryLevel, number> = {
  CP: 1,
  CE1: 0,
  CE2: 0,
  CM1: 0,
  CM2: 0,
};
const NEXT_DICTATION_OPTION = "__next_dictation__";
const DICTATION_PROGRESS_STORAGE_KEY = "copy-challenge-dictation-progress-v1";
const DICTATION_PROGRESS_EVENT = "copy-challenge-dictation-progress";

interface StoredDictationProgress {
  level: PrimaryLevel;
  index: number;
  cursors: Record<PrimaryLevel, number>;
  lettersPerFragment: number;
}

function subscribeToDictationProgress(listener: () => void) {
  if (typeof window === "undefined") return () => undefined;
  window.addEventListener("storage", listener);
  window.addEventListener(DICTATION_PROGRESS_EVENT, listener);
  return () => {
    window.removeEventListener("storage", listener);
    window.removeEventListener(DICTATION_PROGRESS_EVENT, listener);
  };
}

function getDictationProgressSnapshot() {
  if (typeof window === "undefined") return "";
  try {
    return window.localStorage.getItem(DICTATION_PROGRESS_STORAGE_KEY) ?? "";
  } catch {
    return "";
  }
}

function parseStoredDictationProgress(snapshot: string): StoredDictationProgress | null {
  if (!snapshot) return null;
  try {
    const parsed: unknown = JSON.parse(snapshot);
    if (!parsed || typeof parsed !== "object") return null;
    const candidate = parsed as Record<string, unknown>;
    const level = candidate.level;
    const index = candidate.index;
    if (!PRIMARY_LEVELS.some((entry) => entry.id === level) || typeof index !== "number" || !Number.isInteger(index)) return null;

    const cursors = { ...INITIAL_CURSORS };
    if (candidate.cursors && typeof candidate.cursors === "object") {
      const storedCursors = candidate.cursors as Record<string, unknown>;
      for (const entry of PRIMARY_LEVELS) {
        const value = storedCursors[entry.id];
        if (typeof value === "number" && Number.isInteger(value) && value >= 0) cursors[entry.id] = value;
      }
    }

    const levelDefinition = getLevel(level as PrimaryLevel);
    const lettersPerFragment = typeof candidate.lettersPerFragment === "number" && Number.isInteger(candidate.lettersPerFragment)
      ? Math.min(100, Math.max(1, candidate.lettersPerFragment))
      : levelDefinition.recommendedLetters;

    return { level: level as PrimaryLevel, index, cursors, lettersPerFragment };
  } catch {
    return null;
  }
}

function persistDictationProgress(progress: StoredDictationProgress) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(DICTATION_PROGRESS_STORAGE_KEY, JSON.stringify(progress));
    window.dispatchEvent(new Event(DICTATION_PROGRESS_EVENT));
  } catch {
    // The current selection remains available for the current session if storage is blocked.
  }
}

function readStoredLeaderboard(): LeaderboardEntry[] {
  if (typeof window === "undefined") return [];
  try {
    const parsed: unknown = JSON.parse(window.localStorage.getItem(LEADERBOARD_STORAGE_KEY) ?? "[]");
    if (!Array.isArray(parsed)) return [];
    const entries = parsed.filter((entry): entry is LeaderboardEntry => {
      if (!entry || typeof entry !== "object") return false;
      const candidate = entry as Record<string, unknown>;
      return typeof candidate.id === "string" && typeof candidate.score === "number" && typeof candidate.createdAt === "number";
    });
    return sortLeaderboard(entries);
  } catch {
    return [];
  }
}

function persistLeaderboard(entries: readonly LeaderboardEntry[]) {
  try {
    window.localStorage.setItem(LEADERBOARD_STORAGE_KEY, JSON.stringify(entries));
  } catch {
    // The score remains available for the current session when storage is blocked.
  }
}

export function DictaApp() {
  const dictationProgressSnapshot = useSyncExternalStore(subscribeToDictationProgress, getDictationProgressSnapshot, () => "");
  const storedDictationProgress = parseStoredDictationProgress(dictationProgressSnapshot);
  const selectedLevel = storedDictationProgress?.level ?? INITIAL_LEVEL;
  const selectedDictation = getDictation(selectedLevel, storedDictationProgress?.index ?? 0);
  const text = selectedDictation.text;
  const lettersPerFragment = storedDictationProgress?.lettersPerFragment ?? getLevel(selectedLevel).recommendedLetters;
  const [screen, setScreen] = useState<Screen>("setup");
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
  const [summaryScore, setSummaryScore] = useState<number | null>(null);
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>(readStoredLeaderboard);
  const [currentScoreId, setCurrentScoreId] = useState<string | null>(null);
  const [isNewBestScore, setIsNewBestScore] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const [isHelpOpen, setIsHelpOpen] = useState(false);
  const detectorRef = useRef<AttentionDetector | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const screenRef = useRef(screen);
  const phaseRef = useRef(phase);
  const cameraModeRef = useRef(cameraMode);
  const lastCameraReadingAtRef = useRef(0);
  const autoHideBlockedUntilRef = useRef(0);
  const sessionStartedAtRef = useRef<number | null>(null);
  const calibrationReadConfirmedRef = useRef(false);

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

  const chooseNextDictation = (level: PrimaryLevel) => {
    const cursors = storedDictationProgress?.cursors ?? INITIAL_CURSORS;
    const nextDictation = getDictation(level, cursors[level]);
    const nextCursors = { ...cursors, [level]: (nextDictation.index + 1) % nextDictation.total };
    persistDictationProgress({
      level,
      index: nextDictation.index,
      cursors: nextCursors,
      lettersPerFragment: getLevel(level).recommendedLetters,
    });
  };

  const selectLevel = (event: ChangeEvent<HTMLSelectElement>) => {
    if (event.target.value === NEXT_DICTATION_OPTION) {
      chooseNextDictation(selectedLevel);
      return;
    }
    chooseNextDictation(event.target.value as PrimaryLevel);
  };

  const startAutoHideGracePeriod = useCallback(() => {
    autoHideBlockedUntilRef.current = Date.now() + AUTO_HIDE_GRACE_MS;
  }, []);

  const setLetterTarget = (value: number) => {
    if (!Number.isFinite(value)) return;
    const nextLetters = Math.min(100, Math.max(1, Math.round(value)));
    persistDictationProgress({
      level: selectedLevel,
      index: selectedDictation.index,
      cursors: storedDictationProgress?.cursors ?? INITIAL_CURSORS,
      lettersPerFragment: nextLetters,
    });
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
    setIsHelpOpen(false);
    setScreen("placement");
  }, [primeAudioFeedback]);

  useEffect(() => {
    if (screen !== "placement" || !cameraLoading || detectorRef.current) return;
    let cancelled = false;
    const detector = new MediaPipeAttentionDetector({
      analysisFps: 10,
      enterNotebookMs: 220,
      returnScreenMs: 700,
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
      if (phaseRef.current === "memorizing" && Date.now() >= autoHideBlockedUntilRef.current && (!reading.faceDetected || reading.state === "notebook")) setPhase("decision");
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
      if (Date.now() >= autoHideBlockedUntilRef.current && Date.now() - lastCameraReadingAtRef.current > 1200) setPhase("decision");
    }, 250);
    return () => window.clearInterval(watchdog);
  }, [cameraMode, phase, screen]);

  const startCameraSession = useCallback(() => {
    setFragmentIndex(0);
    setReviewCounts(Array(fragments.length).fill(0));
    sessionStartedAtRef.current = Date.now();
    startAutoHideGracePeriod();
    setPhase("memorizing");
    setScreen("session");
  }, [fragments.length, startAutoHideGracePeriod]);

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
        if (calibrationReadConfirmedRef.current) startCameraSession();
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
  }, [calibrationAttempt, cameraMode, fragments.length, playCalibrationBeep, screen, startCameraSession]);

  const confirmCalibrationRead = () => {
    calibrationReadConfirmedRef.current = true;
    if (cameraMode === "manual" || calibrationPhase === "ready") startCameraSession();
  };

  const beginManual = () => {
    stopCamera();
    setCameraLoading(false);
    setCameraMode("manual");
    setCameraError(null);
    setIsHelpOpen(false);
    calibrationReadConfirmedRef.current = false;
    setCalibrationPhase("ready");
    setScreen("calibration-screen");
  };

  const hideFragment = () => setPhase("decision");

  const review = () => {
    setReviewCounts((counts) => counts.map((count, index) => index === fragmentIndex ? count + 1 : count));
    startAutoHideGracePeriod();
    setPhase("memorizing");
  };

  const finishSession = () => {
    const elapsedMs = sessionStartedAtRef.current === null ? 1000 : Date.now() - sessionStartedAtRef.current;
    const score = calculateScore(text, elapsedMs, totalReviews);
    const entry: LeaderboardEntry = {
      id: `${Date.now()}-${score}-${leaderboard.length}`,
      score,
      createdAt: Date.now(),
    };
    const previousBest = leaderboard[0]?.score ?? 0;
    const nextLeaderboard = sortLeaderboard([...leaderboard, entry]);
    setLeaderboard(nextLeaderboard);
    persistLeaderboard(nextLeaderboard);
    setSummaryScore(score);
    setCurrentScoreId(entry.id);
    setIsNewBestScore(score > previousBest);
    sessionStartedAtRef.current = null;
    stopCamera();
    setScreen("summary");
  };

  const next = () => {
    if (fragmentIndex >= fragments.length - 1) {
      finishSession();
      return;
    }
    setFragmentIndex((value) => value + 1);
    startAutoHideGracePeriod();
    setPhase("memorizing");
  };

  const reset = () => {
    stopCamera();
    setCameraLoading(false);
    setIsHelpOpen(false);
    setScreen("setup");
    setPhase("memorizing");
    setFragmentIndex(0);
    setReviewCounts([]);
    setSummaryScore(null);
    setCurrentScoreId(null);
    setIsNewBestScore(false);
    calibrationReadConfirmedRef.current = false;
    sessionStartedAtRef.current = null;
  };

  const prepareNextDictation = () => {
    chooseNextDictation(selectedLevel);
    reset();
  };
  const progressRatio = fragments.length > 0 ? (fragmentIndex + 1) / fragments.length : 0;
  const progressPercent = Math.round(progressRatio * 100);
  const progressColor = `hsl(${Math.round(120 * Math.pow(progressRatio, 1.65))} 72% 52%)`;

  return (
    <main className="app-shell">
      <header className="topbar">
        <div className="brand"><img className="brand-mark" src="/icons/icon-192.png" alt="" aria-hidden="true" />Copy Challenge</div>
        <div className="topbar-actions">
          {screen === "setup" && (
            <button
              className="info-button"
              type="button"
              aria-label="Comment utiliser Copy Challenge"
              aria-expanded={isHelpOpen}
              aria-controls="how-to-use"
              onClick={() => setIsHelpOpen((open) => !open)}
            >
              i
            </button>
          )}
          {screen !== "setup" && <button className="icon-button" aria-label="Quitter la séance" onClick={reset}>×</button>}
        </div>
      </header>

      {screen === "setup" && isHelpOpen && (
        <section id="how-to-use" className="help-panel" aria-label="Comment utiliser Copy Challenge">
          <div className="help-panel-heading">
            <div>
              <div className="eyebrow">Mode d’emploi</div>
              <h2>Comment utiliser Copy Challenge</h2>
            </div>
            <button className="help-close" type="button" aria-label="Fermer les informations" onClick={() => setIsHelpOpen(false)}>×</button>
          </div>
          <ol className="help-list">
            <li><strong>Choisis ta classe</strong><span>Le niveau sélectionne une dictée adaptée. Le numéro de la dictée est indiqué à côté du niveau.</span></li>
            <li><strong>Choisis les lettres par étape</strong><span>La dictée est découpée en petits groupes de mots, sans mélanger deux phrases.</span></li>
            <li><strong>Lis toute la dictée</strong><span>Avec la caméra ou en mode manuel, lis le texte affiché puis appuie sur « J’ai lu ».</span></li>
            <li><strong>Mémorise et écris</strong><span>Regarde le fragment, écris-le sur ton cahier, puis relève les yeux pour continuer.</span></li>
            <li><strong>Revois si nécessaire</strong><span>À chaque étape, choisis « Revoir » ou « Continuer ». Ton score et le classement s’affichent à la fin.</span></li>
          </ol>
        </section>
      )}

      {screen === "setup" && (
        <img
          className="home-banner"
          src="/dicta-banner-tilted-notebook.png"
          alt="Un œil, un cahier et un crayon illustrent la dictée de mémoire."
        />
      )}

      {screen === "setup" && (
        <>
          <section className="card setup-card">
            <div className="setup-heading">
              <label className="field-label" htmlFor="level-select">Niveau de classe</label>
              <span className="dictation-counter">Dictée {selectedDictation.index + 1} / {selectedDictation.total}</span>
            </div>
            <select id="level-select" className="level-select" value={selectedLevel} onChange={selectLevel}>
              {PRIMARY_LEVELS.map((level) => <option key={level.id} value={level.id}>{level.label}</option>)}
              <option value={NEXT_DICTATION_OPTION}>Dictée suivante</option>
            </select>
            <div className="dictation-meta">
              <strong>{getLevel(selectedLevel).label}</strong>
              <span>Dictée {selectedDictation.index + 1} sur {selectedDictation.total} · {getLevel(selectedLevel).cycle} · {text.trim().split(/\s+/).filter(Boolean).length} mots</span>
            </div>
            <div className="settings-row">
              <div><strong>Lettres par étape</strong></div>
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
          <footer className="home-footer">Tous droits réservés, Florence Fauchery</footer>
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
            <button className="primary-button" disabled={!faceDetected || cameraLoading} onClick={() => { primeAudioFeedback(); calibrationReadConfirmedRef.current = false; detectorRef.current?.beginCalibration("screen"); setCalibrationPhase("preparing"); setScreen("calibration-screen"); }}>{faceDetected ? "Mon visage est bien placé" : "Recherche du visage…"}</button>
            <button className="manual-hide" onClick={beginManual}>Utiliser le mode manuel</button>
          </div>
        </section>
      )}

      {screen === "calibration-screen" && (
        <section className="session-shell calibration-shell">
          <div className="card calibration-reading-card" data-phase={calibrationPhase}>
            {calibrationPhase === "failed" ? (
              <>
                <div className="eyebrow">Lis la dictée</div>
                <div className="calibration-dictation">{text}</div>
                <div className="calibration-reading-actions">
                  <p className="calibration-error" role="alert">Replace ton visage dans le champ de la caméra.</p>
                  <button className="secondary-button" onClick={() => { calibrationReadConfirmedRef.current = false; detectorRef.current?.beginCalibration("screen"); setCalibrationPhase("preparing"); setCalibrationAttempt((value) => value + 1); }}>Réessayer</button>
                </div>
              </>
            ) : (
              <>
                <div className="eyebrow">Lis la dictée</div>
                <div className="calibration-dictation">{text}</div>
                <div className="calibration-reading-actions">
                  <p className="calibration-reading-help">Lis toute la dictée, puis appuie quand tu as terminé.</p>
                  <button className="primary-button" onClick={confirmCalibrationRead}>J&apos;ai lu</button>
                </div>
              </>
            )}
          </div>
        </section>
      )}

      {screen === "session" && fragments.length > 0 && (
        <section className="session-shell">
          <div className="progress-wrap">
            <div className="progress-label"><span>Étape {fragmentIndex + 1} sur {fragments.length}</span><span>{progressPercent} %</span></div>
            <div className="progress-track"><div className="progress-bar" style={{ width: `${progressPercent}%`, backgroundColor: progressColor }} /></div>
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
                  <button className="choice-button review" onClick={review}><span className="choice-arrow" aria-hidden="true">↶</span> Revoir</button>
                  <button className="choice-button next" onClick={next}>Continuer <span className="choice-arrow" aria-hidden="true">→</span></button>
                </div>
              </>
            )}
          </div>
        </section>
      )}

      {screen === "summary" && (
        <section className="session-shell summary-shell">
          {totalReviews < 3 && (
            <div className="confetti-field" aria-hidden="true">
              {CONFETTI_PIECES.map((piece, index) => (
                <span
                  key={`${piece.left}-${index}`}
                  className={`confetti-piece confetti-piece-${piece.origin}`}
                  style={{
                    "--confetti-left": piece.left,
                    "--confetti-top": piece.top,
                    "--confetti-delay": piece.delay,
                    "--confetti-drift": piece.drift,
                    "--confetti-rotate": piece.rotate,
                    "--confetti-color": piece.color,
                  } as CSSProperties}
                />
              ))}
            </div>
          )}
          <div className="hero"><h1>Bravo, c’est terminé !</h1></div>
          <div className="card stage-card summary-card">
            <div className={`summary-score ${isNewBestScore ? "summary-score-record" : ""}`}>{summaryScore ?? 0}</div>
            <div className="muted">{isNewBestScore ? "Nouveau record !" : "score"}</div>
            <div className="leaderboard" aria-label="Classement des meilleurs scores">
              <div className="leaderboard-heading"><strong>Classement</strong><span>Meilleurs scores</span></div>
              {leaderboard.length === 0 ? (
                <p className="leaderboard-empty">Ton score apparaîtra ici.</p>
              ) : (
                leaderboard.slice(0, 5).map((entry, index) => {
                  const rank = index + 1;
                  const medal = rank === 1 ? "🥇" : rank === 2 ? "🥈" : rank === 3 ? "🥉" : `#${rank}`;
                  const isMedal = rank <= 3;
                  return (
                    <div className={`leaderboard-row ${entry.id === currentScoreId ? "leaderboard-current-row" : ""}`} key={entry.id}>
                      <span
                        className={`leaderboard-rank ${isMedal ? `medal medal-${rank}` : ""} ${isNewBestScore && isMedal ? "medal-celebration" : ""}`}
                        style={isMedal ? { "--medal-delay": `${index * 130}ms` } as CSSProperties : undefined}
                        aria-label={`Place ${rank}`}
                      >
                        {medal}
                      </span>
                      <strong className="leaderboard-score">{entry.score}</strong>
                      {entry.id === currentScoreId && <span className="leaderboard-current">Toi</span>}
                    </div>
                  );
                })
              )}
            </div>
            <div className="summary-grid summary-grid-single">
              <div className="summary-stat"><strong>{totalReviews}</strong><span>relecture</span></div>
            </div>
            <button className="primary-button" onClick={prepareNextDictation}>Préparer la dictée suivante</button>
          </div>
        </section>
      )}

      {toast && <div className="toast" role="status">{toast}</div>}
    </main>
  );
}
