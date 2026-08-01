"use client";

import { useEffect, useState } from "react";

const OFFLINE_NOTICE_DURATION_MS = 4000;

/** Announces connectivity changes without blocking an active dictation. */
export function OfflineStatus() {
  const [showOfflineNotice, setShowOfflineNotice] = useState(false);

  useEffect(() => {
    let hideTimer: number | null = null;

    const clearHideTimer = () => {
      if (hideTimer !== null) {
        window.clearTimeout(hideTimer);
        hideTimer = null;
      }
    };

    const updateStatus = () => {
      clearHideTimer();

      if (navigator.onLine) {
        setShowOfflineNotice(false);
        return;
      }

      setShowOfflineNotice(true);
      hideTimer = window.setTimeout(() => {
        setShowOfflineNotice(false);
        hideTimer = null;
      }, OFFLINE_NOTICE_DURATION_MS);
    };

    const initialStatusTimer = window.setTimeout(updateStatus, 0);
    window.addEventListener("online", updateStatus);
    window.addEventListener("offline", updateStatus);
    return () => {
      window.clearTimeout(initialStatusTimer);
      clearHideTimer();
      window.removeEventListener("online", updateStatus);
      window.removeEventListener("offline", updateStatus);
    };
  }, []);

  if (!showOfflineNotice) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      style={{
        position: "fixed",
        top: "max(0.75rem, env(safe-area-inset-top))",
        left: "50%",
        zIndex: 60,
        transform: "translateX(-50%)",
        padding: "0.55rem 0.85rem",
        borderRadius: "999px",
        background: "#163a32",
        color: "white",
        fontSize: "0.82rem",
        fontWeight: 650,
        whiteSpace: "nowrap",
        boxShadow: "0 0.45rem 1rem rgba(22, 58, 50, 0.2)",
      }}
    >
      Hors connexion · la séance reste disponible
    </div>
  );
}
