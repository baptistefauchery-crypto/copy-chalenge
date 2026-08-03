"use client";

import { useCallback, useEffect, useRef, useState } from "react";

type SpellingCheckModalState = "capture" | "processing" | "error";

export interface SpellingCheckModalProps {
  onCapture: (image: Blob, canvas: HTMLCanvasElement) => void | Promise<void>;
  onClose: () => void;
  onRetry: () => void;
}

const CAMERA_ERROR_MESSAGE =
  "La caméra n’est pas disponible. Vérifiez l’autorisation puis réessayez.";

export function SpellingCheckModal({
  onCapture,
  onClose,
  onRetry,
}: SpellingCheckModalProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const cameraRequestRef = useRef(0);
  const mountedRef = useRef(false);
  const [state, setState] = useState<SpellingCheckModalState>("capture");
  const [errorMessage, setErrorMessage] = useState("");

  const stopStream = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;

    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
  }, []);

  const startCamera = useCallback(async () => {
    const requestId = cameraRequestRef.current + 1;
    cameraRequestRef.current = requestId;
    stopStream();
    setState("capture");
    setErrorMessage("");

    if (!navigator.mediaDevices?.getUserMedia) {
      setState("error");
      setErrorMessage(CAMERA_ERROR_MESSAGE);
      return;
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: { facingMode: { exact: "environment" } },
      });

      if (!mountedRef.current || requestId !== cameraRequestRef.current) {
        stream.getTracks().forEach((track) => track.stop());
        return;
      }

      streamRef.current = stream;

      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }
    } catch {
      stopStream();
      setState("error");
      setErrorMessage(CAMERA_ERROR_MESSAGE);
    }
  }, [stopStream]);

  useEffect(() => {
    mountedRef.current = true;
    const startTimer = window.setTimeout(() => {
      if (mountedRef.current) void startCamera();
    }, 0);
    return () => {
      window.clearTimeout(startTimer);
      mountedRef.current = false;
      cameraRequestRef.current += 1;
      stopStream();
    };
  }, [startCamera, stopStream]);

  const handleClose = () => {
    cameraRequestRef.current += 1;
    stopStream();
    onClose();
  };

  const handleRetry = () => {
    onRetry();
    void startCamera();
  };

  const handleCapture = async () => {
    const video = videoRef.current;
    if (!video || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) {
      setState("error");
      setErrorMessage("La caméra n’est pas encore prête. Réessayez dans un instant.");
      return;
    }

    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;

    if (!canvas.width || !canvas.height) {
      setState("error");
      setErrorMessage("Impossible de capturer cette image. Réessayez.");
      return;
    }

    const context = canvas.getContext("2d");
    if (!context) {
      setState("error");
      setErrorMessage("Impossible de préparer la photo. Réessayez.");
      return;
    }

    // The preview may be styled as mirrored, but the OCR input must not be.
    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    setState("processing");
    stopStream();

    try {
      const image = await new Promise<Blob>((resolve, reject) => {
        canvas.toBlob((blob) => {
          if (blob) resolve(blob);
          else reject(new Error("Canvas export failed"));
        }, "image/jpeg", 0.92);
      });

      await onCapture(image, canvas);
    } catch (error) {
      setState("error");
      setErrorMessage(error instanceof Error && error.message ? error.message : "La photo n’a pas pu être traitée. Réessayez.");
    }
  };

  return (
    <div
      className="spelling-check-modal-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) handleClose();
      }}
    >
      <section
        className="spelling-check-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="spelling-check-modal-title"
      >
        <div className="spelling-check-modal-heading">
          <div>
            <p className="eyebrow">Vérification</p>
            <h2 id="spelling-check-modal-title">Photographiez votre feuille</h2>
          </div>
          <button className="help-close" type="button" onClick={handleClose} aria-label="Fermer">
            ×
          </button>
        </div>

        <div className="spelling-check-camera" data-state={state}>
          {state !== "error" && (
            <video ref={videoRef} autoPlay muted playsInline aria-label="Aperçu de la caméra arrière" />
          )}
          {state === "capture" && <div className="spelling-check-frame" aria-hidden="true" />}
          {state === "processing" && (
            <div className="spelling-check-overlay" role="status" aria-live="polite">
              Traitement de la photo…
            </div>
          )}
          {state === "error" && (
            <div className="spelling-check-error" role="alert">
              <strong>La photo est indisponible</strong>
              <span>{errorMessage}</span>
            </div>
          )}
        </div>

        {state === "capture" && (
          <p className="spelling-check-help">
            Placez le texte dans le cadre de la caméra arrière, puis prenez une photo nette et bien éclairée.
          </p>
        )}

        <div className="spelling-check-actions">
          {state === "capture" && (
            <button className="primary-button" type="button" onClick={handleCapture}>
              Photographier ma feuille
            </button>
          )}
          {(state === "processing" || state === "error") && (
            <button className="secondary-button" type="button" onClick={handleRetry}>
              Recommencer
            </button>
          )}
        </div>
      </section>
    </div>
  );
}

export default SpellingCheckModal;
