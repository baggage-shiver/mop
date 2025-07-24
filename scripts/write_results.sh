#!/bin/bash
#
# This script writes the results of one experiment project to summary tables (multiple).
# These table include the following:
#   * One table for each algorithm accumulated through all project revisions.
# Arguments:
#   1. Name of the project
#   2. Number of revisions to write the result for [optional]

source ./constants.sh
source ./utils.sh

SCRIPT_DIR=$( cd $( dirname $0 ) && pwd )

PROJECT_NAME="$1"
INCLUDE_BASE_RV="$2"
NUM_REVISIONS="${3:-"$(wc -l "${REVISIONS_DIR}/${PROJECT_NAME}.txt" | xargs | cut -d ' ' -f 1)"}"
CONFIGS=""

PROJECT_DATA_ROOT="${DATA_GENERATED_DATA}/${PROJECT_NAME}"

#######################################
# Checks for input validity of the entire script.
# Will terminate the program if some inputs don't have the right type.
#######################################
function input_check {
  if ! is_number "${NUM_REVISIONS}"; then
    echo "${ERROR_LABEL} NUM_REVISIONS has to be a number."
    exit 1
  fi
}

#######################################
# Prints the header of a summary table and save to an auto-generated path.
# Specifically, it contains the following headers:
#   * # of affected specs
#   * Monitoring time
#   * # of impacted classes
#   * # of impacted methods
#   * Test time w/o. MOP
#   * # of violations
#   * # of monitors
#   * # of events
# Note that some entries will not be available depending on the algorithm used.
# (For instance, class-level RPS will not have data for "# of impacted methods,"
# and method-level RPS will not have data for "# of impacted classes")
# Arguments:
#   The result table to print in.
#######################################
function print_header {
  local result_file="$1"
  printf "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n"\
      "SHA"\
      "#AFFECTED_SPECS"\
      "TIME(S)"\
      "#TOTAL_CLASSES"\
      "#IMPACTED_CLASSES"\
      "#IMPACTED_METHODS"\
      "TEST(S)"\
      "#VIOLATIONS"\
      "#MONITORS"\
      "#EVENTS"\
      > ${result_file}
}

#######################################
# Prints one row of the summary table based on specific project, commit, and algorithm.
# Arguments:
#   The result table to print in.
#   The project to print in.
#   A commit hash for a project.
#   The algorithm in use (from ${ALGORITHMS})
#######################################
function print_row {
  local result_file="$1"
  local project="$2"
  local version="$3"
  local algorithm="$4"
  printf "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n"\
      "${version}"\
      "$(get_count "${project}" "${version}" "${algorithm}" affected_specs)"\
      "$(get_time_s "${project}" "${version}" "${algorithm}" "total")"\
      "$(get_count "${project}" "${version}" "${algorithm}" "total_classes")"\
      "$(get_count "${project}" "${version}" "${algorithm}" "impacted_classes")"\
      "$(get_count "${project}" "${version}" "${algorithm}" "impacted_methods")"\
      "$(get_time_s ${project} ${version} "test" "total")"\
      "$(get_num_violations ${project} ${version} ${algorithm})"\
      "$(get_count "${project}" "${version}" "${algorithm}" "monitor")"\
      "$(get_count "${project}" "${version}" "${algorithm}" "event")"\
      >> ${result_file}
  # Old ways of collecting entries:
  # "$(get_num_affected_specs ${project} ${version} ${algorithm})"\
  # "$(get_entry ${project} ${version} ${algorithm} ${IMPACTED_CLASSES_ENTRY} 3)"\
  # "$(get_entry ${project} ${version} ${algorithm} ${IMPACTED_METHODS_ENTRY} 3)"\
}

#######################################
# Output the number of affected specs for a particular project at a particular version,
# with the selected algorithm. If the algorithm does not generate this entry
# (such as simply running "test"), then it will print nothing.
# Arguments:
#   The project to print in.
#   A commit hash for a project.
#   The algorithm in use (from ${ALGORITHMS})
#######################################
function get_num_affected_specs {
  local project="$1"
  local version="$2"
  local algorithm="$3"

  local spec_list="${PROJECT_DATA_ROOT}/${version}/${STARTS}-${algorithm}/${SPEC_LIST_NAME}"
  if [ "${algorithm}" = "test" ] || [ "${algorithm}" = "VMS" ]; then
    if [ -f "${spec_list}" ]; then
      echo -n "$(wc -l "${spec_list}" | xargs | cut -d ' ' -f 1)"
    else
      echo -n "0"
    fi
  else
    get_entry "${project}" "${version}" "${algorithm}" "${AFFECTED_SPECS_ENTRY}" 3
  fi
}

#######################################
# Output a relevant entry in a log for a particular project at a particular version,
# with the selected algorithm. If the algorithm does not generate this entry
# (such as simply running "test"), then it will print nothing.
# Arguments:
#   The project to print in.
#   A commit hash for a project.
#   The algorithm in use (from ${ALGORITHMS})
#   The Prefix of the entry to search for. For instance, "INFO: ImpactedMethods:" can be one.
#   Index of the keyword to search, using ' ' as the delimiter.
#######################################
function get_entry {
  local project="$1"
  local version="$2"
  local algorithm="$3"
  local entry="$4"
  local index="$5"

  local log_file="${PROJECT_DATA_ROOT}/${version}/${algorithm}${LOG_FILE_NAME}"
  if [ -f "${log_file}" ]; then
    if [[ ! -z $(grep -F "${entry}" "${log_file}") ]]; then
      echo -n "$(grep -F "${entry}" "${log_file}" | cut -d ' ' -f 3)"
    else
      echo -n "0"
    fi
  else
    echo -n "0"
  fi
}

#######################################
# Output the number of violations for a particular project at a particular version,
# with the selected algorithm. If the algorithm does not generate this entry
# (such as simply running "test"), then it will print nothing.
# Arguments:
#   The project to print in.
#   A commit hash for a project.
#   The algorithm in use (from ${ALGORITHMS})
#######################################
function get_num_violations {
  local project="$1"
  local version="$2"
  local algorithm="$3"

  local violation_counts="${PROJECT_DATA_ROOT}/${version}/${algorithm}-${VIOLATION_COUNTS}"
  if [ -f "${violation_counts}" ]; then
    echo -n "$(wc -l "${violation_counts}" | xargs | cut -d ' ' -f 1)"
  else
    echo -n "0"
  fi
}

#######################################
# Summarizes tables produced in experiment run w.r.t a specific trait.
# For instance, one can collect execution time for different algorithms in a project to one summary
# table, by specifying which column in each algorithm's table to collect.
# Arguments:
#   The project to print in.
#   Which column to collect information and summarize.
#   The output file of this summary.
#######################################
function summarize_tables {
  local project="$1"
  local field_index="$2"
  local output_file="$3"

  echo "${LOG_LABEL} --- Writing ${output_file}"
  arguments="<(cut -d ',' -f ${SHA_INDEX} ${TABLES_DIR}/${project}/test.csv)"
  for algorithm in "${CONFIGS[@]}"; do
    arguments="${arguments} <(cut -d ',' -f ${field_index} ${TABLES_DIR}/${project}/${algorithm}.csv)"
  done
  eval "paste -d ',' ${arguments} > ${output_file}"
  # Rewrite header
  new_header="SHA"
  for algorithm in "${CONFIGS[@]}"; do
    new_header="${new_header},${algorithm}"
  done
  sed -i.tmp "1s/.*/${new_header}/" "${output_file}"
  rm "${output_file}.tmp"
  # Calculate and apply summary rows at bottom
  counter=2
  sum_row="sum"
  arith_mean_row="arith_mean"
  geo_mean_row="geo_mean"
  for algorithm in "${CONFIGS[@]}"; do
    sum="$(cut -d ',' -f ${counter} "${output_file}" | grep -v ${algorithm} |  awk '{ print $1 }' | paste -sd+ - | bc -l)"
    arith_mean="$(cut -d ',' -f ${counter} "${output_file}" | grep -v ${algorithm} | awk '$1>0{tot+=$1; c++} END {m=tot/c; printf "%.2f\n", m}' 2> ${DEV_NULL})"
    if [ -z "${arith_mean}" ]; then
      arith_mean=0
    fi
    geo_mean="$(cut -d ',' -f ${counter} "${output_file}" | grep -v ${algorithm} | awk 'BEGIN{E = exp(1);} $1>0{tot+=log($1); c++} END{m=tot/c; printf "%.2f\n", E^m}' 2> ${DEV_NULL})"
    if [ -z "${geo_mean}" ]; then
      geo_mean=0
    fi
    sum_row="${sum_row},${sum}"
    arith_mean_row="${arith_mean_row},${arith_mean}"
    geo_mean_row="${geo_mean_row},${geo_mean}"
    ((counter++))
  done
  echo "${sum_row}" >> "${output_file}"
  echo "${arith_mean_row}" >> "${output_file}"
  echo "${geo_mean_row}" >> "${output_file}"
}

#######################################
# Write algorithm tables for a project.
# It will produce tables with names in this format: ${project}-${algorithm}.csv
# Globals:
#   PROJECT_NAME
#   PROJECT_DATA_ROOT
#   NUM_REVISIONS
#######################################
function write_algorithm_tables {
  for algorithm in "${CONFIGS[@]}"; do
    RESULT_FILE="${TABLES_DIR}/${PROJECT_NAME}/${algorithm}.csv"
    echo "${LOG_LABEL} --- Writing ${RESULT_FILE}"
    print_header "${RESULT_FILE}"
    for version in $(grep -v ^# "${REVISIONS_DIR}/${PROJECT_NAME}.txt" | head "-${NUM_REVISIONS}"); do
      # TODO: Hard-coded
      if [ -n "$(grep "Base RV" ${DATA_GENERATED_DATA}/${PROJECT_NAME}/${version}/CLASSES_ps1c-log.txt)" ] && [ "${INCLUDE_BASE_RV}" = "false" ]; then continue; fi
      # if [ -n "$(grep "No impacted" ${DATA_GENERATED_DATA}/${PROJECT_NAME}/${version}/CLASSES_ps1c-log.txt)" ]; then continue; fi
      # Write as much as there are data
      if [ ! -d "${PROJECT_DATA_ROOT}/${version}" ]; then
        break
      fi
      print_row "${RESULT_FILE}" "${PROJECT_NAME}" "${version}" "${algorithm}"
    done
    append_summary_rows "${RESULT_FILE}"
    (
      cd "${PROJECT_DATA_ROOT}"
      find . -name "${algorithm}-${VIOLATION_COUNTS}" | xargs cat | cut -d ' ' -f 3,9 | sort | uniq > "${VIOLATIONS}/${PROJECT_NAME}-${algorithm}-${VIOLATION_COUNTS}"
    )
  done
}

#######################################
# Print out set differences between two violations files.
# Will first check files and then print out.
# Arguments:
#   The violation file that will subtract the other.
#   The violation file that will be subtracted off the first one.
#   The output file that will record the differences in violations.
# Outputs:
#   The cardinality of the differences of the two sets.
#######################################
function get_set_diff {
  local former="$1"
  local latter="$2"
  local output="$3"

  if [ ! -f "${former}" ]; then
    result=""
  elif [ ! -f "${latter}" ]; then
    result=$(cat ${former} | cut -d ' ' -f 3,9 | sort)
  else
    result=$(diff <(cat ${former} | cut -d ' ' -f 3,9 | sort | uniq) <(cat ${latter} | cut -d ' ' -f 3,9 | sort | uniq) | grep "^<" | cut -d ' ' -f 2-)
  fi
  echo "${result}" > "${output}"
  if [ -z "${result}" ]; then
    echo 0
  else
    echo "${result}" | wc -l | xargs
  fi
}

#######################################
# Write one violations table for a project to a designated file.
# It will contain the following columns, where "\" is set exclusion:
#   SHA
#   VMS\HyRTS
#   HyRTS\VMS
#   VMS\CLASSES
#   CLASSES\VMS
#   VMS\METHODS
#   METHODS\VMS
#   VMS\MOP
#   MOP\VMS
#   VMS\FINE
#   FINE\VMS
# Globals:
#   PROJECT_NAME
#   PROJECT_DATA_ROOT
#   NUM_REVISIONS
#######################################
function write_violations_table {
  local output_file="${TABLES_DIR}/${PROJECT_NAME}-safety.csv"
  echo "${LOG_LABEL} --- Writing ${output_file}"
  printf "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n"\
      "SHA"\
      "VMS\\HyRTS"\
      "HyRTS\\VMS"\
      "VMS\\CLASSES"\
      "CLASSES\\VMS"\
      "VMS\\METHODS"\
      "METHODS\\VMS"\
      "VMS\\MOP"\
      "MOP\\VMS"\
      "VMS\\FINE"\
      "FINE\\VMS"\
      > ${output_file}
  while read -r version; do
    # Write as much as there are data
    if [ ! -d "${PROJECT_DATA_ROOT}/${version}" ]; then
      break
    fi
    printf "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n"\
        "${version}"\
        "$(get_set_diff \
            ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/HyRTS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/VMS\\HyRTS)"\
        "$(get_set_diff \
            ${PROJECT_DATA_ROOT}/${version}/HyRTS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/HyRTS\\VMS)"\
        "$(get_set_diff \
            ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/CLASSES-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/VMS\\CLASSES)"\
        "$(get_set_diff \
            ${PROJECT_DATA_ROOT}/${version}/CLASSES-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/CLASSES\\VMS)"\
        "$(get_set_diff \
            ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/METHODS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/VMS\\METHODS)"\
        "$(get_set_diff \
            ${PROJECT_DATA_ROOT}/${version}/METHODS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/METHODS\\VMS)"\
        "$(get_set_diff \
            ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/MOP-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/VMS\\MOP)"\
        "$(get_set_diff \
            ${PROJECT_DATA_ROOT}/${version}/MOP-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/MOP\\VMS)"\
        "$(get_set_diff \
            ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/FINE-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/VMS\\FINE)"\
        "$(get_set_diff \
            ${PROJECT_DATA_ROOT}/${version}/FINE-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/FINE\\VMS)"\
        >> ${output_file}
  done < <(grep -v ^# "${REVISIONS_DIR}/${PROJECT_NAME}.txt" | head "-${NUM_REVISIONS}")
}

#######################################
# Write an alternative format of safety table.
# Arguments:
#   The variant to group the algorithms for, e.g. ps1c, ps3cl, etc.
#######################################
function write_violations_table_alt {
  local variant=$1
  local output_file="${TABLES_DIR}/${PROJECT_NAME}_${variant}_safety-alt.csv"
  echo "${LOG_LABEL} --- Writing ${output_file}"

  # Print header
  {
    # VMS\MOP could be a good indicator for false positive, or flaky tests.
    echo -n "SHA,|MOP|,|VMS|,|VMS\\MOP|"
    for algorithm in "${ALGORITHMS[@]}"; do
      if ! [[ "${algorithm}" == *"${variant}"* || " ${RTS_ALGORITHMS[@]} " =~ " ${algorithm} " ]]; then continue; fi
      echo -n ",|VMS\\${algorithm}|,|${algorithm}\\VMS|"
    done
    for rts in "${RTS_ALGORITHMS[@]}"; do
      algorithm="${rts}+CLASSES_${variant}"
      echo -n ",|VMS\\${algorithm}|,|${algorithm}\\VMS|"
    done
    best=$(grep "^${PROJECT_NAME}" ${SCRIPT_DIR}/../data/best_${variant}.csv | cut -d ',' -f 2)
    for rts in "${RTS_ALGORITHMS[@]}"; do
      algorithm="${rts}+${best}_${variant}"
      echo -n ",|VMS\\${algorithm}|,|${algorithm}\\VMS|"
    done
    echo ""
  } > "${output_file}"

  # Print body
  while read -r version; do
    # Write as much as there are data
    if [ ! -d "${PROJECT_DATA_ROOT}/${version}" ]; then break; fi
    {
      echo -n "${version}"
      # The ternary operator here is used for mitigating the case where the experiment is in progress.
      echo -n ",$([ -f ${PROJECT_DATA_ROOT}/${version}/MOP-${VIOLATION_COUNTS} ] && wc -l ${PROJECT_DATA_ROOT}/${version}/MOP-${VIOLATION_COUNTS} | xargs | cut -d ' ' -f 1 || echo 0)"
      echo -n ",$([ -f ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} ] && wc -l ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} | xargs | cut -d ' ' -f 1 || echo 0)"
      echo -n ",$(get_set_diff \
          ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
          ${PROJECT_DATA_ROOT}/${version}/MOP-${VIOLATION_COUNTS} \
          ${PROJECT_DATA_ROOT}/${version}/VMS\\MOP)"
      for algorithm in "${ALGORITHMS[@]}"; do
        if ! [[ "${algorithm}" == *"${variant}"* || " ${RTS_ALGORITHMS[@]} " =~ " ${algorithm} " ]]; then continue; fi
        echo -n ",$(get_set_diff \
            ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/${algorithm}-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/VMS\\${algorithm})"
        echo -n ",$(get_set_diff \
            ${PROJECT_DATA_ROOT}/${version}/${algorithm}-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/${algorithm}\\VMS)"
      done
      for rts in "${RTS_ALGORITHMS[@]}"; do
        algorithm="${rts}+CLASSES_${variant}"
        echo -n ",$(get_set_diff \
            ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/${algorithm}-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/VMS\\${algorithm})"
        echo -n ",$(get_set_diff \
            ${PROJECT_DATA_ROOT}/${version}/${algorithm}-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/${algorithm}\\VMS)"
      done
      best=$(grep "^${PROJECT_NAME}" ${SCRIPT_DIR}/../data/best_${variant}.csv | cut -d ',' -f 2)
      for rts in "${RTS_ALGORITHMS[@]}"; do
        algorithm="${rts}+${best}_${variant}"
        echo -n ",$(get_set_diff \
            ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/${algorithm}-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/VMS\\${algorithm})"
        echo -n ",$(get_set_diff \
            ${PROJECT_DATA_ROOT}/${version}/${algorithm}-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/VMS-${VIOLATION_COUNTS} \
            ${PROJECT_DATA_ROOT}/${version}/${algorithm}\\VMS)"
      done
      echo ""
    } >> "${output_file}"
  done < <(grep -v ^# "${REVISIONS_DIR}/${PROJECT_NAME}.txt" | head "-${NUM_REVISIONS}")

  # Print footer
  append_summary_rows "${output_file}"
}

function auto_detect_configs {
  pushd ${PROJECT_DATA_ROOT} &> ${DEV_NULL}
    mapfile -t CONFIGS < <(find . -name "*-log.txt" | grep -v "setup" | cut -d '/' -f 3 | sort | uniq | sed 's/-log.txt//g')
  popd &> ${DEV_NULL}
}

input_check
setup_environment_variables &> "${DEV_NULL}"
if [ ! -d "${PROJECT_DATA_ROOT}" ]; then
  echo "Data for ${PROJECT_NAME} does not exist at ${PROJECT_DATA_ROOT}"
  exit 1
fi
if [ ! -d "${TABLES_DIR}" ]; then
  mkdir -p "${TABLES_DIR}"
fi
auto_detect_configs
: '
(
  cd "local_dependencies/impacted-method-estimator"
  mvn clean package
) &> /dev/null
'
# Override previous results
rm -rf "${TABLES_DIR}/${PROJECT_NAME}"
mkdir -p "${TABLES_DIR}/${PROJECT_NAME}"
echo "${LOG_LABEL} -- Writing results for ${PROJECT_NAME}"

mkdir -p "${VIOLATIONS}"
write_algorithm_tables
write_violations_table
write_violations_table_alt "ps1c"
write_violations_table_alt "ps3cl"
#summarize_tables "${PROJECT_NAME}" "${EXECUTION_TIME_INDEX}" "${TABLES_DIR}/${PROJECT_NAME}/time.csv"
#summarize_tables "${PROJECT_NAME}" "${VIOLATION_COUNTS_INDEX}" "${TABLES_DIR}/${PROJECT_NAME}/violations.csv"
