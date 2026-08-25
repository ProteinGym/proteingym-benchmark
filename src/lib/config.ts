export const HUGGINGFACE_MANIFESTS_URL = "/manifests.json";

export const HUGGINGFACE_DATASET_BASE_URL =
  "https://huggingface.co/datasets/ProteinGym/ProteinGym2.0/resolve/main";

export function getDatasetDownloadUrl(datasetName: string): string {
  return `${HUGGINGFACE_DATASET_BASE_URL}/${datasetName}.pgdata`;
}
