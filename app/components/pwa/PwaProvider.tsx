"use client";

import { useEffect, useState, type CSSProperties } from "react";

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

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

/**
 * Registers the offline worker and presents Android's native install prompt.
 * Render once near the application root. The banner stays out of the way when
 * the app is already installed or the browser cannot offer installation.
 */
export function PwaProvider() {
  const [installEvent, setInstallEvent] =
    useState<BeforeInstallPromptEvent | null>(null);
  const [updateReady, setUpdateReady] = useState(false);

  useEffect(() => {
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
    };
    const confirmInstallation = () => {
      setInstallEvent(null);
    };

    window.addEventListener("beforeinstallprompt", captureInstallPrompt);
    window.addEventListener("appinstalled", confirmInstallation);
    return () => {
      removeServiceWorkerListener?.();
      window.removeEventListener("beforeinstallprompt", captureInstallPrompt);
      window.removeEventListener("appinstalled", confirmInstallation);
    };
  }, []);

  if (!installEvent && !updateReady) return null;

  const requestInstallation = async () => {
    if (!installEvent) return;
    await installEvent.prompt();
    const { outcome } = await installEvent.userChoice;
    if (outcome === "accepted") setInstallEvent(null);
  };

  const applyUpdate = () => window.location.reload();

  return (
    <>
      {installEvent && (
        <aside
          aria-label="Installation de l'application"
          style={{ ...noticeStyle, bottom: updateReady ? "6.5rem" : "1rem" }}
        >
          <span style={{ fontSize: "0.92rem", lineHeight: 1.35 }}>
            Installez Dicta sur cet appareil pour la retrouver comme une application.
          </span>
          <button type="button" onClick={requestInstallation} style={noticeButtonStyle}>
            Installer
          </button>
        </aside>
      )}
      {updateReady && (
        <aside aria-label="Mise à jour disponible" style={noticeStyle}>
          <span style={{ fontSize: "0.92rem", lineHeight: 1.35 }}>
            Une nouvelle version de Dicta est disponible.
          </span>
          <button type="button" onClick={applyUpdate} style={noticeButtonStyle}>
            Mettre à jour
          </button>
        </aside>
      )}
    </>
  );
}
