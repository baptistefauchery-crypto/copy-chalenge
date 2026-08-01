"use client";

import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type ChangeEvent } from "react";
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
const CONFETTI_PIECES = [
  { left: "6%", delay: "0ms", drift: "-24px", rotate: "-12deg", color: "#ef765f" },
  { left: "13%", delay: "180ms", drift: "18px", rotate: "24deg", color: "#6654d9" },
  { left: "21%", delay: "420ms", drift: "-12px", rotate: "-8deg", color: "#58a37c" },
  { left: "29%", delay: "80ms", drift: "28px", rotate: "18deg", color: "#f3b34f" },
  { left: "37%", delay: "520ms", drift: "-18px", rotate: "-22deg", color: "#ef765f" },
  { left: "45%", delay: "240ms", drift: "16px", rotate: "12deg", color: "#6654d9" },
  { left: "53%", delay: "640ms", drift: "-26px", rotate: "-16deg", color: "#58a37c" },
  { left: "61%", delay: "140ms", drift: "22px", rotate: "20deg", color: "#f3b34f" },
  { left: "69%", delay: "360ms", drift: "-14px", rotate: "-6deg", color: "#ef765f" },
  { left: "77%", delay: "700ms", drift: "20px", rotate: "28deg", color: "#6654d9" },
  { left: "85%", delay: "300ms", drift: "-22px", rotate: "-18deg", color: "#58a37c" },
  { left: "93%", delay: "560ms", drift: "12px", rotate: "10deg", color: "#f3b34f" },
  { left: "9%", delay: "860ms", drift: "30px", rotate: "-26deg", color: "#6654d9" },
  { left: "17%", delay: "1040ms", drift: "-20px", rotate: "16deg", color: "#ef765f" },
  { left: "25%", delay: "760ms", drift: "14px", rotate: "-10deg", color: "#f3b34f" },
  { left: "33%", delay: "920ms", drift: "-28px", rotate: "22deg", color: "#58a37c" },
  { left: "41%", delay: "1180ms", drift: "24px", rotate: "-18deg", color: "#ef765f" },
  { left: "49%", delay: "820ms", drift: "-16px", rotate: "8deg", color: "#6654d9" },
  { left: "57%", delay: "980ms", drift: "26px", rotate: "-24deg", color: "#f3b34f" },
  { left: "65%", delay: "1120ms", drift: "-20px", rotate: "14deg", color: "#58a37c" },
  { left: "73%", delay: "780ms", drift: "18px", rotate: "-8deg", color: "#ef765f" },
  { left: "81%", delay: "1060ms", drift: "-26px", rotate: "26deg", color: "#6654d9" },
  { left: "89%", delay: "900ms", drift: "16px", rotate: "-14deg", color: "#58a37c" },
  { left: "97%", delay: "1200ms", drift: "-12px", rotate: "18deg", color: "#f3b34f" },
] as const;

const INITIAL_LEVEL: PrimaryLevel = "CP";
const INITIAL_DICTATION = getDictation(INITIAL_LEVEL, 0);
const INITIAL_CURSORS: Record<PrimaryLevel, number> = {
  CP: 1,
  CE1: 0,
  CE2: 0,
  CM1: 0,
  CM2: 0,
};
const NEXT_DICTATION_OPTION = "__next_dictation__";

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
  const [summaryScore, setSummaryScore] = useState<number | null>(null);
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>(readStoredLeaderboard);
  const [currentScoreId, setCurrentScoreId] = useState<string | null>(null);
  const [isNewBestScore, setIsNewBestScore] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const detectorRef = useRef<AttentionDetector | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const screenRef = useRef(screen);
  const phaseRef = useRef(phase);
  const cameraModeRef = useRef(cameraMode);
  const lastCameraReadingAtRef = useRef(0);
  const autoHideBlockedUntilRef = useRef(0);
  const sessionStartedAtRef = useRef<number | null>(null);
  const dictationCursorsRef = useRef<Record<PrimaryLevel, number>>({ ...INITIAL_CURSORS });

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
    const nextDictation = getDictation(level, dictationCursorsRef.current[level]);
    dictationCursorsRef.current[level] = (nextDictation.index + 1) % nextDictation.total;
    setSelectedLevel(level);
    setSelectedDictation(nextDictation);
    setText(nextDictation.text);
    setLettersPerFragment(getLevel(level).recommendedLetters);
  };

  const selectLevel = (event: ChangeEvent<HTMLSelectElement>) => {
    if (event.target.value === NEXT_DICTATION_OPTION) {
      chooseNextDictation(selectedLevel);
      return;
    }
    chooseNextDictation(event.target.value as PrimaryLevel);
  };

  const startAutoHideGracePeriod = () => {
    autoHideBlockedUntilRef.current = Date.now() + AUTO_HIDE_GRACE_MS;
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
        sessionStartedAtRef.current = Date.now();
        startAutoHideGracePeriod();
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
    sessionStartedAtRef.current = Date.now();
    startAutoHideGracePeriod();
  };

  const hideFragment = () => setPhase("decision");

  const review = () => {
    setReviewCounts((counts) => counts.map((count, index) => index === fragmentIndex ? count + 1 : count));
    startAutoHideGracePeriod();
    setPhase("memorizing");
  };

  const finishSession = () => {
    const elapsedMs = sessionStartedAtRef.current === null ? 1000 : Date.now() - sessionStartedAtRef.current;
    const score = calculateScore(text, elapsedMs);
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
    setScreen("setup");
    setPhase("memorizing");
    setFragmentIndex(0);
    setReviewCounts([]);
    setSummaryScore(null);
    setCurrentScoreId(null);
    setIsNewBestScore(false);
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
        {screen !== "setup" && <button className="icon-button" aria-label="Quitter la séance" onClick={reset}>×</button>}
      </header>

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
        <section className="session-shell">
          <div className="hero"><div className="eyebrow">Dictée terminée</div><h1>Bravo, c’est terminé !</h1><p>Chaque relecture aide à mieux connaître sa mémoire.</p></div>
          <div className="card stage-card summary-card">
            {totalReviews < 3 && (
              <div className="confetti-field" aria-hidden="true">
                {CONFETTI_PIECES.map((piece, index) => (
                  <span
                    key={`${piece.left}-${index}`}
                    className="confetti-piece"
                    style={{
                      "--confetti-left": piece.left,
                      "--confetti-delay": piece.delay,
                      "--confetti-drift": piece.drift,
                      "--confetti-rotate": piece.rotate,
                      "--confetti-color": piece.color,
                    } as CSSProperties}
                  />
                ))}
              </div>
            )}
            <div className={`summary-score ${isNewBestScore ? "summary-score-record" : ""}`}>{summaryScore ?? 0}</div>
            <div className="muted">{isNewBestScore ? "Nouveau record !" : "score"}</div>
            <div className="leaderboard" aria-label="Classement des meilleurs scores">
              <div className="leaderboard-heading"><strong>Classement</strong><span>Meilleurs scores</span></div>
              {leaderboard.length === 0 ? (
                <p className="leaderboard-empty">Ton score apparaîtra ici.</p>
              ) : (
                leaderboard.map((entry, index) => {
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
            <div className="summary-grid">
              <div className="summary-stat"><strong>{totalReviews}</strong><span>relectures</span></div>
              <div className="summary-stat"><strong>{reviewCounts.filter(Boolean).length}</strong><span>fragments revus</span></div>
            </div>
            <button className="primary-button" onClick={prepareNextDictation}>Préparer la dictée suivante</button>
          </div>
        </section>
      )}

      {toast && <div className="toast" role="status">{toast}</div>}
    </main>
  );
}
