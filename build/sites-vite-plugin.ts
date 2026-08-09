import { access, cp, mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { relative, resolve, sep } from "node:path";
import type { Plugin } from "vite";

async function exists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      return false;
    }
    throw error;
  }
}

async function listFiles(directory: string): Promise<string[]> {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(async (entry) => {
    const path = resolve(directory, entry.name);
    return entry.isDirectory() ? listFiles(path) : [path];
  }));
  return nested.flat();
}

// Packages Sites metadata and migrations after Vite finishes compiling.
export function sites(): Plugin {
  let root = process.cwd();

  return {
    name: "sites",
    apply: "build",
    configResolved(config) {
      root = config.root;
    },
    async closeBundle() {
      const clientDirectory = resolve(root, "dist", "client");
      const serviceWorkerPath = resolve(clientDirectory, "sw.js");
      const outputDirectory = resolve(root, "dist", ".openai");
      const hostingConfig = resolve(root, ".openai", "hosting.json");
      const drizzleSource = resolve(root, "drizzle");

      await rm(outputDirectory, { recursive: true, force: true });
      await mkdir(outputDirectory, { recursive: true });

      if (await exists(hostingConfig)) {
        await cp(hostingConfig, resolve(outputDirectory, "hosting.json"));
      }
      if (await exists(drizzleSource)) {
        await cp(drizzleSource, resolve(outputDirectory, "drizzle"), {
          recursive: true,
        });
      }

      if (await exists(serviceWorkerPath)) {
        const assets = (await listFiles(clientDirectory))
          .map((path) => `/${relative(clientDirectory, path).split(sep).join("/")}`)
          .filter((asset) => asset !== "/sw.js" && asset !== "/_headers" && !asset.startsWith("/."))
          .sort();
        const source = await readFile(serviceWorkerPath, "utf8");
        const generated = assets.map((asset) => JSON.stringify(asset)).join(",\n  ");
        await writeFile(serviceWorkerPath, source.replace('"/__BUILD_ASSETS__"', generated), "utf8");
      }
    },
  };
}
