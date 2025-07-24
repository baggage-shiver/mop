#!/bin/bash
#
# Run experiments in parallel with Docker.

source ./constants.sh
source ./utils.sh

PROJECT_LIST="$1"
MAX_JOBS="$2"
STATS="$3"
MAX_REVS="${4:-}"

for project_url in $(cat ${PROJECT_LIST}); do
  echo "bash run_single_with_docker.sh ${project_url} ${STATS} ${MAX_REVS}"
done > commands.txt

cat commands.txt | parallel -j "${MAX_JOBS}"
