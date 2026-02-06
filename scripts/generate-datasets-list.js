import fs from "fs";
import path from "path";
import { execSync } from "child_process";
import TOML from "smol-toml";

const datasetsOriginalDir = path.resolve("datasets/original");
const datasetsSplitsDir = path.resolve("datasets/splits");
const staticDir = path.resolve("static");
const tempDir = path.resolve(".temp-datasets");

fs.mkdirSync(tempDir, { recursive: true });
fs.mkdirSync(path.join(staticDir, "datasets"), { recursive: true });

const originalFiles = fs.existsSync(datasetsOriginalDir) ? fs.readdirSync(datasetsOriginalDir).filter(f => f.endsWith(".pgdata")) : [];
const splitsFiles = fs.existsSync(datasetsSplitsDir) ? fs.readdirSync(datasetsSplitsDir).filter(f => f.endsWith(".splits.pgdata")) : [];

const datasetMap = new Map();
originalFiles.forEach(f => {
  const baseName = f.replace(".pgdata", "");
  if (!datasetMap.has(baseName)) datasetMap.set(baseName, {});
  datasetMap.get(baseName).original = f;
});
splitsFiles.forEach(f => {
  const baseName = f.replace(".splits.pgdata", "");
  if (!datasetMap.has(baseName)) datasetMap.set(baseName, {});
  datasetMap.get(baseName).splits = f;
});

const slugs = [];

for (const [baseName, files] of datasetMap) {
  const archivePath = files.original 
    ? path.join(datasetsOriginalDir, files.original)
    : path.join(datasetsSplitsDir, files.splits);
  const extractDir = path.join(tempDir, baseName);
  
  fs.mkdirSync(extractDir, { recursive: true });
  
  try {
    execSync(`unzip -q -o "${archivePath}" manifest.lock -d "${extractDir}"`);
    
    const manifestPath = path.join(extractDir, "manifest.lock");
    if (fs.existsSync(manifestPath)) {
      const manifestContent = fs.readFileSync(manifestPath, "utf-8");
      const manifest = TOML.parse(manifestContent);
      const datasetName = manifest.name || baseName;
      
      manifest._original_filename = files.original || null;
      manifest._splits_filename = files.splits || null;
      
      fs.writeFileSync(
        path.join(staticDir, "datasets", `${datasetName}.toml`),
        TOML.stringify(manifest)
      );
      
      slugs.push(datasetName);
      console.log(`Extracted ${datasetName} from ${files.original || files.splits}`);
    }
  } catch (error) {
    console.warn(`Failed to extract ${baseName}:`, error.message);
  }
}

fs.rmSync(tempDir, { recursive: true, force: true });

fs.writeFileSync(
  path.join(staticDir, "datasets-list.json"),
  JSON.stringify({ slugs }, null, 2)
);

console.log(`Generated datasets-list.json with ${slugs.length} datasets`);
