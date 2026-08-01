import type { AttentionState } from "./types";

export type StabilizerOptions = {
  enterNotebookMs?: number;
  returnScreenMs?: number;
  minimumConfidence?: number;
};

/** Temporal hysteresis prevents one noisy frame from hiding/showing the text. */
export class AttentionStabilizer {
  private stable: AttentionState = "unknown";
  private candidate: AttentionState = "unknown";
  private candidateSince = 0;
  private readonly notebookMs: number;
  private readonly screenMs: number;
  private readonly minimumConfidence: number;

  constructor(options: StabilizerOptions = {}) {
    this.notebookMs = options.enterNotebookMs ?? 300;
    this.screenMs = options.returnScreenMs ?? 500;
    this.minimumConfidence = options.minimumConfidence ?? 0.18;
  }

  update(raw: AttentionState, confidence: number, timestamp: number): AttentionState {
    if (raw === "unknown" || confidence < this.minimumConfidence) return this.stable;
    if (raw === this.stable) {
      this.candidate = "unknown";
      return this.stable;
    }
    if (raw !== this.candidate) {
      this.candidate = raw;
      this.candidateSince = timestamp;
      return this.stable;
    }
    const duration = raw === "notebook" ? this.notebookMs : this.screenMs;
    if (timestamp - this.candidateSince >= duration) {
      this.stable = raw;
      this.candidate = "unknown";
    }
    return this.stable;
  }

  /** Missing face is fail-closed: consumers must immediately hide displayed text. */
  loseFace(): AttentionState {
    this.stable = "notebook";
    this.candidate = "unknown";
    return this.stable;
  }

  reset() {
    this.stable = "unknown";
    this.candidate = "unknown";
    this.candidateSince = 0;
  }
}
