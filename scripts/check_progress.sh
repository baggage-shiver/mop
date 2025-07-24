#!/bin/bash
#
# Checks the progress of the parallel run.
# Assumes that the project is run commit-by-commit.

source ./constants.sh

MAX_REVS="${1:-20}" # TODO: If time permits, do 50.

counter=1
while read -r log; do
  project_name=$(echo ${log} | cut -d '.' -f 1)
  at=$(grep -F "${LOG_LABEL}" ${LOGS_DIR}/${log} | grep ']$' | tail -1 | cut -d '[' -f 3 | cut -d '/' -f 1)
  total=$(cat ${REVISIONS_DIR}/${log} | grep -v "#" | wc -l | xargs | cut -d ' ' -f 1)
  total=$(( total > MAX_REVS ? MAX_REVS : total ))
  echo -ne "${counter}\t${project_name}: "
  echo -n "${at}/${total}"
  if [[ ${at} -eq ${total} ]]; then
      echo " [COMPLETED]"
  else
      echo ""
  fi
  # Uncomment the next line if you want to see where exactly the run is at
  # tail -1 ${LOGS_DIR}/${log}
  ((counter=counter+1))
done < <(ls "${LOGS_DIR}")
