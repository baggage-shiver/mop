#!/bin/bash
#
# Runs experiments in a parallel manner.
# Arguments:
#   A list of URLs for experiment projects.
#   The maximum number of jobs that are happening simultaneously.
#   Whether to run with the statistical version or not, use "stats" to enable statistics.
#   The maximum number of revisions that are used in each project, optional.

source ./constants.sh
source ./utils.sh

PROJECT_LIST="$1"
MAX_JOBS="$2"
STATS="$3"
NUM_REVISIONS="${4:-}"

function input_check {
  if ! is_number "${MAX_JOBS}"; then 
    echo "${ERROR_LABEL} MAX_JOBS has to be a number."
    exit 1
  fi
}

input_check
mkdir -p "${LOGS_DIR}"
for project_url in $(cat ${PROJECT_LIST}); do
  project_name="$(get_project_name "${project_url}")"
  echo "bash run_experiment.sh ${project_url} ${STATS} ${NUM_REVISIONS} &> logs/${project_name}.txt"
done > commands.txt

cat commands.txt | parallel -j "${MAX_JOBS}"
