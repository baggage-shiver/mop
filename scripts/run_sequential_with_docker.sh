#!/bin/bash
#
# Run experiments in sequential with Docker.

source ./constants.sh
source ./utils.sh

PROJECT_LIST="$1"
STATS="$2"
MAX_REVS="${3:-}"
RUN_CONFIGS="${4:-}"

while IFS= read -r project_url; do
  bash run_single_with_docker.sh "${project_url}" "${STATS}" "${MAX_REVS}" "${RUN_CONFIGS}"
done < "${PROJECT_LIST}"
