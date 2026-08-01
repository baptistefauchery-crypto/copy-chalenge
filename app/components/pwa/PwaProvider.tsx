"use client";

import { useEffect, useState } from "react";

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

/**
 * Registers the offline worker and presents Android's native install prompt.
 * Render once near the application root. The banner stays out of the way when
 * the app is already installed or the browser cannot offer installation.
 */
export function PwaProvider() {
  const [installEvent, setInstallEvent] =
    useState<BeforeInstallPromptEvent | null>(null);

  useEffect(() => {
    let removeServiceWorkerListener: (() => void) | undefined;
    if ("serviceWorker" in navigator) {
      const hadController = Boolean(navigator.serviceWorker.controller);
      let reloading = false;
      const reloadOnUpdate = () => {
        if (!hadController || reloading) return;
        reloading = true;
        window.location.reload();
      };
      navigator.serviceWorker.addEventListener("controllerchange", reloadOnUpdate);
      navigator.serviceWorker
        .register("/sw.js", { scope: "/", updateViaCache: "none" })
        .then((registration) => registration.update())
        .catch(() => {
          // The application remains usable online if registration is unavailable.
        });
      removeServiceWorkerListener = () => navigator.serviceWorker.removeEventListener("controllerchange", reloadOnUpdate);
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

  if (!installEvent) return null;

  const requestInstallation = async () => {
    await installEvent.prompt();
    const { outcome } = await installEvent.userChoice;
    if (outcome === "accepted") setInstallEvent(null);
  };

  return (
    <aside
      aria-label="Installation de l'application"
      style={{
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
      }}
    >
      <span style={{ fontSize: "0.92rem", lineHeight: 1.35 }}>
        Installez Dicta sur cet appareil pour la retrouver comme une application.
      </span>
      <button
        type="button"
        onClick={requestInstallation}
        style={{
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
        }}
      >
        Installer
      </button>
    </aside>
  );
}
