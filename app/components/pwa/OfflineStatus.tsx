"use client";

import { useEffect, useState } from "react";

/** Announces connectivity changes without blocking an active dictation. */
export function OfflineStatus() {
  const [online, setOnline] = useState(true);

  useEffect(() => {
    const updateStatus = () => setOnline(navigator.onLine);
    const initialStatusTimer = window.setTimeout(updateStatus, 0);
    window.addEventListener("online", updateStatus);
    window.addEventListener("offline", updateStatus);
    return () => {
      window.clearTimeout(initialStatusTimer);
      window.removeEventListener("online", updateStatus);
      window.removeEventListener("offline", updateStatus);
    };
  }, []);

  if (online) return null;

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
