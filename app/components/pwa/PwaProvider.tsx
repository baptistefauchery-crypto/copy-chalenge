"use client";

import { useEffect, useState, type CSSProperties } from "react";

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

type StandaloneNavigator = Navigator & { standalone?: boolean };

const UPDATE_CHECK_INTERVAL_MS = 15 * 60 * 1000;
const noticeStyle: CSSProperties = {
  position: "fixed",
  right: "1rem",
  bottom: "1rem",
  left: "1rem",
  zIndex: 50,
  display: "flex",
  alignItems: "center",
  justifyContent: "space-between",
  gap: "1rem",
  maxWidth: "30rem",
  marginInline: "auto",
  padding: "0.8rem 0.9rem",
  border: "1px solid rgba(22, 58, 50, 0.18)",
  borderRadius: "1rem",
  background: "#fffdf8",
  color: "#163a32",
  boxShadow: "0 0.75rem 2rem rgba(22, 58, 50, 0.18)",
};

const noticeButtonStyle: CSSProperties = {
  flexShrink: 0,
  minHeight: "2.75rem",
  padding: "0 1rem",
  border: 0,
  borderRadius: "999px",
  background: "#163a32",
  color: "white",
  font: "inherit",
  fontWeight: 700,
  cursor: "pointer",
};

const installNoticeStyle: CSSProperties = {
  ...noticeStyle,
  flexDirection: "column",
  alignItems: "stretch",
  padding: 0,
  border: 0,
  background: "transparent",
  boxShadow: "none",
};

const installButtonStyle: CSSProperties = {
  ...noticeButtonStyle,
  width: "100%",
  minHeight: "4.25rem",
  padding: "0 1.25rem",
  borderRadius: "1.1rem",
  fontSize: "1.05rem",
  fontWeight: 800,
  boxShadow: "0 0.75rem 1.5rem rgba(22, 58, 50, 0.24)",
};

const installHelpNoticeStyle: CSSProperties = {
  ...installNoticeStyle,
  padding: "0.9rem",
  border: "1px solid rgba(22, 58, 50, 0.18)",
  background: "#fffdf8",
  boxShadow: "0 0.75rem 2rem rgba(22, 58, 50, 0.18)",
};

function isInstalledApp() {
  return (
    window.matchMedia("(display-mode: standalone)").matches ||
    window.matchMedia("(display-mode: fullscreen)").matches ||
    (navigator as StandaloneNavigator).standalone === true ||
    document.referrer.startsWith("android-app://")
  );
}

/**
 * Registers the offline worker and presents Android's native install prompt.
 * Render once near the application root. The install button stays hidden when
 * the app is already installed or the browser cannot offer installation.
 */
export function PwaProvider() {
  const [installEvent, setInstallEvent] =
    useState<BeforeInstallPromptEvent | null>(null);
  const [isInstalled, setIsInstalled] = useState<boolean | null>(null);
  const [installHelp, setInstallHelp] = useState(false);
  const [updateReady, setUpdateReady] = useState(false);

  useEffect(() => {
    const installationCheckTimer = window.setTimeout(
      () => setIsInstalled(isInstalledApp()),
      0,
    );
    const checkInstalledMode = () => setIsInstalled(isInstalledApp());
    const standaloneMediaQuery = window.matchMedia("(display-mode: standalone)");
    document.addEventListener("visibilitychange", checkInstalledMode);
    standaloneMediaQuery.addEventListener("change", checkInstalledMode);
    let removeServiceWorkerListener: (() => void) | undefined;
    if ("serviceWorker" in navigator) {
      const hadController = Boolean(navigator.serviceWorker.controller);
      let registration: ServiceWorkerRegistration | null = null;
      let updateTimer: number | undefined;
      const markUpdateReady = () => {
        if (hadController) setUpdateReady(true);
      };
      const checkForUpdate = () => {
        if (document.visibilityState !== "visible") return;
        void registration?.update().catch(() => undefined);
      };
      const checkWhenVisible = () => checkForUpdate();
      const checkWhenOnline = () => checkForUpdate();

      navigator.serviceWorker.addEventListener("controllerchange", markUpdateReady);
      document.addEventListener("visibilitychange", checkWhenVisible);
      window.addEventListener("online", checkWhenOnline);
      navigator.serviceWorker
        .register("/sw.js", { scope: "/", updateViaCache: "none" })
        .then((nextRegistration) => {
          registration = nextRegistration;
          checkForUpdate();
          updateTimer = window.setInterval(checkForUpdate, UPDATE_CHECK_INTERVAL_MS);
          if (hadController && nextRegistration.waiting) setUpdateReady(true);
        })
        .catch(() => {
          // The application remains usable online if registration is unavailable.
        });
      removeServiceWorkerListener = () => {
        navigator.serviceWorker.removeEventListener("controllerchange", markUpdateReady);
        document.removeEventListener("visibilitychange", checkWhenVisible);
        window.removeEventListener("online", checkWhenOnline);
        if (updateTimer !== undefined) window.clearInterval(updateTimer);
      };
    }

    const captureInstallPrompt = (event: Event) => {
      event.preventDefault();
      setInstallEvent(event as BeforeInstallPromptEvent);
      setInstallHelp(false);
    };
    const confirmInstallation = () => {
      setInstallEvent(null);
      setIsInstalled(true);
    };

    window.addEventListener("beforeinstallprompt", captureInstallPrompt);
    window.addEventListener("appinstalled", confirmInstallation);
    return () => {
      window.clearTimeout(installationCheckTimer);
      document.removeEventListener("visibilitychange", checkInstalledMode);
      standaloneMediaQuery.removeEventListener("change", checkInstalledMode);
      removeServiceWorkerListener?.();
      window.removeEventListener("beforeinstallprompt", captureInstallPrompt);
      window.removeEventListener("appinstalled", confirmInstallation);
    };
  }, []);

  const showInstallButton = isInstalled === false;

  if (!showInstallButton && !updateReady) return null;

  const requestInstallation = async () => {
    if (!installEvent) {
      setInstallHelp(true);
      return;
    }
    await installEvent.prompt();
    const { outcome } = await installEvent.userChoice;
    if (outcome === "accepted") setInstallEvent(null);
  };

  const applyUpdate = () => window.location.reload();

  return (
    <>
      {showInstallButton && (
        <aside
          aria-label="Installation de l'application"
          style={{
            ...(installHelp ? installHelpNoticeStyle : installNoticeStyle),
            bottom: updateReady ? "6.75rem" : "1rem",
          }}
        >
          <button
            type="button"
            aria-label="Installer Copy Challenge sur ce téléphone"
            onClick={requestInstallation}
            style={installButtonStyle}
          >
            Installer Copy Challenge sur ce téléphone
          </button>
          {installHelp && (
            <span
              role="status"
              style={{
                padding: "0.7rem 0.85rem 0.85rem",
                color: "#716d7e",
                fontSize: "0.82rem",
                lineHeight: 1.4,
                textAlign: "center",
              }}
            >
              Pour une vraie application sans barre d’adresse, ouvre ce lien dans
              Google Chrome puis choisis ⋮ → « Installer l’application ».
              « Ajouter à l’écran d’accueil » crée seulement un raccourci.
            </span>
          )}
        </aside>
      )}
      {updateReady && (
        <aside aria-label="Mise à jour disponible" style={noticeStyle}>
          <span style={{ fontSize: "0.92rem", lineHeight: 1.35 }}>
            Une nouvelle version de Copy Challenge est disponible.
          </span>
          <button type="button" onClick={applyUpdate} style={noticeButtonStyle}>
            Mettre à jour
          </button>
        </aside>
      )}
    </>
  );
}
