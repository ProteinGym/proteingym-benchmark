import fs from "fs";
import path from "path";
import { execSync } from "child_process";
import TOML from "smol-toml";

const datasetsDir = path.resolve("datasets");
const staticDir = path.resolve("static");
const tempDir = path.resolve(".temp-datasets");

fs.mkdirSync(tempDir, { recursive: true });
fs.mkdirSync(path.join(staticDir, "datasets"), { recursive: true });

const files = fs.readdirSync(datasetsDir);
const pgdataFiles = files.filter((file) => file.endsWith(".pgdata"));
const slugs = [];

for (const file of pgdataFiles) {
  const archivePath = path.join(datasetsDir, file);
  const extractDir = path.join(tempDir, file.replace(".pgdata", ""));
  
  fs.mkdirSync(extractDir, { recursive: true });
  
  try {
    execSync(`unzip -q -o "${archivePath}" manifest.lock -d "${extractDir}"`);
    
    const manifestPath = path.join(extractDir, "manifest.lock");
    if (fs.existsSync(manifestPath)) {
      const manifestContent = fs.readFileSync(manifestPath, "utf-8");
      const manifest = TOML.parse(manifestContent);
      const datasetName = manifest.name || file.replace(".pgdata", "");
      
      manifest._archive_filename = file;
      
      fs.writeFileSync(
        path.join(staticDir, "datasets", `${datasetName}.toml`),
        TOML.stringify(manifest)
      );
      
      slugs.push(datasetName);
      console.log(`Extracted ${datasetName} from ${file}`);
    }
  } catch (error) {
    console.warn(`Failed to extract ${file}:`, error.message);
  }
}

fs.rmSync(tempDir, { recursive: true, force: true });

fs.writeFileSync(
  path.join(staticDir, "datasets-list.json"),
  JSON.stringify({ slugs }, null, 2)
);

console.log(`Generated datasets-list.json with ${slugs.length} datasets`);
