import json
import shutil
import subprocess
from pathlib import Path
from proteingym.base import Subsets

datasets_splits_dir = Path("datasets/splits")
static_dir = Path("static")
temp_dir = Path(".temp-datasets")

temp_dir.mkdir(parents=True, exist_ok=True)
(static_dir / "datasets").mkdir(parents=True, exist_ok=True)

splits_files = list(datasets_splits_dir.glob("*.splits.pgdata")) if datasets_splits_dir.exists() else []

slugs = []
all_datasets = []

for splits_file in splits_files:
    base_name = splits_file.stem.replace(".splits", "")
    extract_dir = temp_dir / base_name
    extract_dir.mkdir(parents=True, exist_ok=True)
    
    subsets = Subsets.from_path(splits_file)
    splits = list(subsets.slices.keys())
    
    dataset = subsets[splits[0]].dataset
    dataset_name = dataset.name
    
    subprocess.run(["unzip", "-q", "-o", str(splits_file), "dataset.pgdata", "-d", str(extract_dir)], check=True)
    subprocess.run(["unzip", "-q", "-o", str(extract_dir / "dataset.pgdata"), "manifest.lock", "-d", str(extract_dir)], check=True)
    
    manifest_path = extract_dir / "manifest.lock"
    if manifest_path.exists():
        shutil.copy(manifest_path, static_dir / "datasets" / f"{dataset_name}.lock")
        slugs.append(dataset_name)
    
    for split in splits:
        dataset = subsets[split].dataset
        targets = [target.name for target in dataset.assay_targets]
        
        for target in targets:
            all_datasets.append({
                "name": base_name,
                "input_filename": str(splits_file.absolute()),
                "split": split,
                "target": target
            })
    
    print(f"Processed {base_name}: {len(splits)} splits, {len(targets)} targets")

shutil.rmtree(temp_dir, ignore_errors=True)

with open(static_dir / "datasets-list.json", "w") as f:
    json.dump({"slugs": slugs}, f, indent=2)

with open("benchmark/supervised/datasets.json", "w") as f:
    json.dump({"datasets": all_datasets}, f, indent=2)

with open("benchmark/zero_shot/datasets.json", "w") as f:
    json.dump({"datasets": all_datasets}, f, indent=2)

print(f"Generated datasets-list.json with {len(slugs)} datasets")
print(f"Generated datasets.json with {len(all_datasets)} entries")
