import assert from "node:assert/strict";
import test from "node:test";

import { classifyFeatures, createCalibration, createScreenCalibration } from "../app/lib/vision/calibration.ts";
import { AttentionStabilizer } from "../app/lib/vision/smoothing.ts";
import type { AttentionFeatures, CalibrationSample } from "../app/lib/vision/types.ts";

const features = (overrides: Partial<AttentionFeatures>): AttentionFeatures => ({
  headPitch: 0.18,
  headYaw: 0,
  leftIrisX: 0.5,
  leftIrisY: 0.45,
  rightIrisX: 0.5,
  rightIrisY: 0.45,
  leftEyeOpen: 0.3,
  rightEyeOpen: 0.3,
  ...overrides,
});

const calibrationSample = (mean: AttentionFeatures): CalibrationSample => ({
  mean,
  deviation: features({
    headPitch: 0.02,
    headYaw: 0.02,
    leftIrisX: 0.02,
    leftIrisY: 0.02,
    rightIrisX: 0.02,
    rightIrisY: 0.02,
    leftEyeOpen: 0.02,
    rightEyeOpen: 0.02,
  }),
  sampleCount: 30,
});

test("separates a screen look from a downward notebook look", () => {
  const calibration = createCalibration(
    calibrationSample(features({ headPitch: 0.18, leftIrisY: 0.45, rightIrisY: 0.45 })),
    calibrationSample(features({ headPitch: 0.27, leftIrisY: 0.65, rightIrisY: 0.64 })),
  );

  assert.ok(calibration.quality > 0.85);
  assert.equal(classifyFeatures(calibration.screen.mean, calibration).state, "screen");
  assert.equal(classifyFeatures(calibration.notebook.mean, calibration).state, "notebook");
});

test("does not claim that almost identical poses are separated", () => {
  const calibration = createCalibration(
    calibrationSample(features({ headPitch: 0.18, leftIrisY: 0.45, rightIrisY: 0.45 })),
    calibrationSample(features({ headPitch: 0.185, leftIrisY: 0.46, rightIrisY: 0.455 })),
  );

  assert.ok(calibration.quality < 0.85);
  assert.equal(classifyFeatures(calibration.screen.mean, calibration).state, "screen");
  assert.ok(classifyFeatures(calibration.screen.mean, calibration).confidence >= 0.45);
});

test("recognizes the calibrated screen pose without a notebook calibration", () => {
  const screen = calibrationSample(features({ headPitch: 0.18, leftIrisY: 0.45, rightIrisY: 0.45 }));
  const calibration = createScreenCalibration(screen);

  assert.equal(classifyFeatures(screen.mean, calibration).state, "screen");
  assert.equal(
    classifyFeatures(features({ headPitch: 0.29, leftIrisY: 0.67, rightIrisY: 0.66 }), calibration).state,
    "notebook",
  );
  assert.equal(
    classifyFeatures(features({ headYaw: 0.18, leftIrisX: 0.72, rightIrisX: 0.72 }), calibration).state,
    "notebook",
  );
});

test("keeps screen when either the head or both eyes still point at it", () => {
  const screen = calibrationSample(features({}));
  const calibration = createScreenCalibration(screen);

  assert.equal(
    classifyFeatures(features({ leftIrisX: 0.75, rightIrisX: 0.75 }), calibration).state,
    "screen",
    "head facing the screen is sufficient",
  );
  assert.equal(
    classifyFeatures(features({ headPitch: 0.29, headYaw: 0.16 }), calibration).state,
    "screen",
    "eyes pointing at the screen are sufficient",
  );
  assert.equal(
    classifyFeatures(features({ headPitch: 0.29, headYaw: 0.16, leftIrisX: 0.75, rightIrisX: 0.75 }), calibration).state,
    "notebook",
    "both head and eyes must be away",
  );
});

test("leaving the camera frame immediately exits the screen state", () => {
  const stabilizer = new AttentionStabilizer({ returnScreenMs: 100 });
  stabilizer.update("screen", 1, 0);
  assert.equal(stabilizer.update("screen", 1, 100), "screen");

  assert.equal(stabilizer.loseFace(), "notebook");
});
