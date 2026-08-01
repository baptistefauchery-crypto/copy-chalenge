import type { AttentionFeatures, FaceLandmark } from "./types";

const distance = (a: FaceLandmark, b: FaceLandmark) =>
  Math.hypot(a.x - b.x, a.y - b.y, (a.z ?? 0) - (b.z ?? 0));

const average = (points: FaceLandmark[]): FaceLandmark => ({
  x: points.reduce((sum, point) => sum + point.x, 0) / points.length,
  y: points.reduce((sum, point) => sum + point.y, 0) / points.length,
  z: points.reduce((sum, point) => sum + (point.z ?? 0), 0) / points.length,
});

const ratioBetween = (value: number, first: number, second: number) =>
  (value - first) / Math.max(Math.abs(second - first), 1e-5);

/**
 * Extracts scale-independent pose/eye values from the 478 point MediaPipe mesh.
 * Returns null for incomplete meshes so callers can fail closed.
 */
export function extractAttentionFeatures(
  landmarks: readonly FaceLandmark[],
): AttentionFeatures | null {
  if (landmarks.length < 478) return null;

  const get = (index: number) => landmarks[index];
  const leftOuter = get(33);
  const leftInner = get(133);
  const rightInner = get(362);
  const rightOuter = get(263);
  const eyeMid = average([leftOuter, leftInner, rightInner, rightOuter]);
  const faceWidth = Math.max(distance(get(234), get(454)), 1e-5);
  const faceHeight = Math.max(distance(get(10), get(152)), 1e-5);
  const nose = get(1);

  const leftIris = average([get(468), get(469), get(470), get(471), get(472)]);
  const rightIris = average([get(473), get(474), get(475), get(476), get(477)]);
  const leftTop = average([get(159), get(160), get(158)]);
  const leftBottom = average([get(145), get(144), get(153)]);
  const rightTop = average([get(386), get(385), get(387)]);
  const rightBottom = average([get(374), get(380), get(373)]);

  return {
    // These are robust pose proxies. Calibration turns them into user-specific signals.
    headPitch: (nose.y - eyeMid.y) / faceHeight,
    headYaw: (nose.x - eyeMid.x) / faceWidth,
    leftIrisY: ratioBetween(leftIris.y, leftTop.y, leftBottom.y),
    rightIrisY: ratioBetween(rightIris.y, rightTop.y, rightBottom.y),
    leftEyeOpen: distance(leftTop, leftBottom) / Math.max(distance(leftOuter, leftInner), 1e-5),
    rightEyeOpen: distance(rightTop, rightBottom) / Math.max(distance(rightOuter, rightInner), 1e-5),
  };
}
