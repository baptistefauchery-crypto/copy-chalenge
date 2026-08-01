import assert from "node:assert/strict";
import test from "node:test";

import { classifyFeatures, createCalibration } from "../app/lib/vision/calibration.ts";
import type { AttentionFeatures, CalibrationSample } from "../app/lib/vision/types.ts";

const features = (overrides: Partial<AttentionFeatures>): AttentionFeatures => ({
  headPitch: 0.18,
  headYaw: 0,
  leftIrisY: 0.45,
  rightIrisY: 0.45,
  leftEyeOpen: 0.3,
  rightEyeOpen: 0.3,
  ...overrides,
});

const calibrationSample = (mean: AttentionFeatures): CalibrationSample => ({
  mean,
  deviation: features({ headPitch: 0.02, leftIrisY: 0.02, rightIrisY: 0.02 }),
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
