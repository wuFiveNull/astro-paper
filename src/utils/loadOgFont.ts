import { readFile } from "node:fs/promises";
import { resolve, sep } from "node:path";

export async function loadOgFont(fontPath: string): Promise<Buffer> {
  const clientAssets = resolve(process.cwd(), "dist/client");
  const relativePath = fontPath.replace(/^[/\\]+/, "");
  const assetPath = resolve(clientAssets, relativePath);

  if (!assetPath.startsWith(`${clientAssets}${sep}`)) {
    throw new Error("Invalid generated font asset path.");
  }

  return readFile(assetPath);
}
