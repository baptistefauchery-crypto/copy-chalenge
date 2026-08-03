import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { join } from "node:path";
import test from "node:test";

test("checks French spelling offline with suggestions", async () => {
  const previousFetch = globalThis.fetch;
  globalThis.fetch = (async (input: RequestInfo | URL) => {
    const relativePath = String(input).replace(/^[/\\]+/u, "");
    const bytes = await readFile(join(process.cwd(), "public", relativePath));
    return new Response(bytes, { status: 200 });
  }) as typeof fetch;

  try {
    const { checkFrenchSpelling } = await import("../app/lib/spelling/french.ts");
    const incorrect = await checkFrenchSpelling("L’ecole est belle.");
    const correct = await checkFrenchSpelling("L’école est belle.");

    assert.equal(incorrect.issues[0]?.word, "L’ecole");
    assert.ok(incorrect.issues[0]?.suggestions.includes("école"));
    assert.equal(correct.issues.length, 0);
  } finally {
    globalThis.fetch = previousFetch;
  }
});
