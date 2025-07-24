#!/bin/bash
#
# Runs experiments in a sequential manner.
# Arguments:
#   A list of URLs for experiment projects.
#   Whether to run with the statistical version or not, use "stats" to enable statistics.
#   The maximum number of revisions that are used in each project, optional.

source ./constants.sh
source ./utils.sh

PROJECT_LIST="$1"
STATS="$2"
NUM_REVISIONS="${3:-}"

mkdir -p "${LOGS_DIR}"
for project_url in $(cat ${PROJECT_LIST}); do
  project_name="$(get_project_name "${project_url}")"
  echo "Running experiment for project ${project_name}"
  bash run_experiment.sh ${project_url} ${STATS} ${NUM_REVISIONS} &> logs/${project_name}.txt
done

