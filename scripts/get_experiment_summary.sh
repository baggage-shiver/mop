#!/bin/bash
#
# Obtain experiment summary by writing all result tables for every project as well as a summary table.
# Arguments:
#   A list of project URLs.

source ./constants.sh
source ./utils.sh

PROJECT_LIST="$1"
INCLUDE_BASE_RV="$2" # Include revisions that revert to base RV into the table.
MAX_JOBS="${3:-10}"

#######################################
# Prints the header of a summary table for all projects.
# Columns:
#   project name
#   execution time (sum)
#   violations (sum)
#   execution time (artihmetic mean)
#   violations (artihmetic mean)
#   execution time (geometric mean)
#   violations (geometric mean)
# Arguments:
#   The result table to print in.
#######################################
function print_header {
  local result_file="$1"
  printf "%s,%s,%s,%s,%s,%s,%s\n"\
      "project"\
      "time (sum)"\
      "violations (sum)"\
      "time (arith mean)"\
      "violations (arith mean)"\
      "time (geo mean)"\
      "violations (geo mean)"\
      > ${result_file}
}

#######################################
# Prints a row of a summary table for all projects.
# Arguments:
#   The result table to print in.
#   Project name.
#   Algorithm to build table for.
#   The column that the algorithm data exist at.
#######################################
function print_row {
  local result_file="$1"
  local project_name="$2"
  local algorithm="$3"
  local algorithm_col="$4"

  printf "%s,%s,%s,%s,%s,%s,%s\n"\
    "${project_name}"\
    "$(tail -3 ${TABLES_DIR}/${project_name}-time.csv | head -1 | cut -d ',' -f ${algorithm_col})"\
    "$(tail -3 ${TABLES_DIR}/${project_name}-violations.csv | head -1 | cut -d ',' -f ${algorithm_col})"\
    "$(tail -2 ${TABLES_DIR}/${project_name}-time.csv | head -1 | cut -d ',' -f ${algorithm_col})"\
    "$(tail -2 ${TABLES_DIR}/${project_name}-violations.csv | head -1 | cut -d ',' -f ${algorithm_col})"\
    "$(tail -1 ${TABLES_DIR}/${project_name}-time.csv | cut -d ',' -f ${algorithm_col})"\
    "$(tail -1 ${TABLES_DIR}/${project_name}-violations.csv | cut -d ',' -f ${algorithm_col})"\
    >> ${result_file}
}

#######################################
# Find and categorize the project and commit that produce suspicious violation results.
# The seven types of suspicious cases (categories) are as follows:
#   1: [Safety] |VMS\HyRTS| > 0: HyRTS is unsafe.
#   2: [Safety] |VMS\METHODS| > 0: Method level RPS is unsafe.
#   3: [Safety] |VMS\CLASSES| > 0: Class level RPS is unsafe.
#   4: [Non-determinism] |VMS\MOP| > 0: If MOP is unsafe, then it is likely non-determinism.
#   5: [Precision] |HyRTS\VMS| > |CLASSES\VMS|: HyRTS should not be more imprecise than class level RPS.
#   6: [Precision] |METHODS\VMS| > |HyRTS\VMS|: Method level RPS should not be more imprecise than HyRTS.
#   7: [Precision] |METHODS\VMS| > |CLASSES\VMS|: Method level RPS should not be more imprecise than class level RPS.
# Globals:
#   PROJECT_LIST
# Output:
#   A table saved to $INSPECTION_FILE with the following columns:
#     Category
#     Project
#     SHA
#######################################
function create_inspection_file {
  echo "category,project,SHA" > "${INSPECTION_FILE}"
  while read -r url; do
    project_name=$(get_project_name ${url})
    {
      read # Skip header
      while IFS=, read -r sha vms_m_hyrts hyrts_m_vms vms_m_classes classes_m_vms vms_m_methods methods_m_vms vms_m_mop mop_m_vms vms_m_fine fine_m_vms; do
        # One commit can have multiple things worth inspection, so no elif
        if [[ $(("${vms_m_hyrts}")) > 0 ]]; then
          echo "${HYRTS_UNSAFE},${project_name},${sha}" >> "${INSPECTION_FILE}"
        fi
        if [[ $(("${vms_m_methods}")) > 0 ]]; then
          echo "${METHODS_UNSAFE},${project_name},${sha}" >> "${INSPECTION_FILE}"
        fi
        if [[ $(("${vms_m_classes}")) > 0 ]]; then
          echo "${CLASSES_UNSAFE},${project_name},${sha}" >> "${INSPECTION_FILE}"
        fi
        if [[ $(("${vms_m_mop}")) > 0 ]]; then
          echo "${NON_DETERMINISM},${project_name},${sha}" >> "${INSPECTION_FILE}"
        fi
        if [[ $(("${hyrts_m_vms}")) > $(("${classes_m_vms}")) ]]; then
          echo "${HYRTS_CLASSES_IMPRECISE},${project_name},${sha}" >> "${INSPECTION_FILE}"
        fi
        if [[ $(("${methods_m_vms}")) > $(("${hyrts_m_vms}")) ]]; then
          echo "${METHODS_HYRTS_IMPRECISE},${project_name},${sha}" >> "${INSPECTION_FILE}"
        fi
        if [[ $(("${methods_m_vms}")) > $(("${classes_m_vms}")) ]]; then
          echo "${METHODS_CLASSES_IMPRECISE},${project_name},${sha}" >> "${INSPECTION_FILE}"
        fi
        if [[ $(("${vms_m_fine}")) > 0 ]]; then
          echo "${FINE_UNSAFE},${project_name},${sha}" >> "${INSPECTION_FILE}"
        fi
      done
    } < "${TABLES_DIR}/${project_name}-safety.csv"
  done < "${PROJECT_LIST}"
}

#######################################
# Create a summary of safety data.
#######################################
function get_summary_safety_table {
  local variant=$1
  
  local output_file="${TABLES_DIR}/${variant}_safety-alt.csv"
  # Print header
  {
    # VMS\MOP could be a good indicator for false positive, or flaky tests.
    echo -n "project,|MOP|,|VMS|,|VMS\\MOP|"
    for algorithm in "${ALGORITHMS[@]}"; do
      if [[ ! "${algorithm}" == *"${variant}"* ]]; then continue; fi
      echo -n ",|VMS\\${algorithm}|,|${algorithm}\\VMS|"
    done
    echo ""
  } > "${output_file}"
  while read -r url; do
    project_name=$(get_project_name ${url})
    tail -3 "${TABLES_DIR}/${project_name}_${variant}_safety-alt.csv" | head -1 | sed "s/sum/${project_name}/g" >> "${output_file}"
  done < "${PROJECT_LIST}"
}

#######################################
# Print the 9-row table that contains:
# 1. Missed new violations
# 2. Unsafe revisions
# 3. Unsafe projects
#######################################
function get_three_row_table {
  local variant=$1

  local output_file="${TABLES_DIR}/3-row-${variant}.csv"
  {
    # Print header
    echo -n "CATEGORY"
    for algorithm in "${ALGORITHMS[@]}"; do
      if [[ "${algorithm}" == *"${variant}"* ]]; then
        echo -n ",${algorithm}"
      fi
    done
    echo ""
    # Missed new violations
    echo -n "MISSED_V(pre-flaky out of ?),"
    while IFS=, read -r sha vms vmc cmv vmf fmv vmh hmv vmm mmv vmhs hsmv vmms msmv; do
      echo "${vmc},${vmf},${vmh},${vmm},${vmhs},${vmms}"
    done < <(tac "${TABLES_DIR}/${variant}_safety-alt.csv" | head -3 | tail -1)
    # TODO
    echo -n "MISSED_V(post-flaky out of ?),"
    while IFS=, read -r sha vms vmc cmv vmf fmv vmh hmv vmm mmv vmhs hsmv vmms msmv; do
      echo "${vmc},${vmf},${vmh},${vmm},${vmhs},${vmms}"
    done < <(tac "${TABLES_DIR}/${variant}_safety-alt.csv" | head -3 | tail -1)
    # TODO
    echo -n "MISSED_V(post-flaky out of ?),"
    while IFS=, read -r sha vms vmc cmv vmf fmv vmh hmv vmm mmv vmhs hsmv vmms msmv; do
      echo "${vmc},${vmf},${vmh},${vmm},${vmhs},${vmms}"
    done < <(tac "${TABLES_DIR}/${variant}_safety-alt.csv" | head -3 | tail -1)
    # Unsafe revisions
    echo -n "UNSAFE_R,"
    local cu=0
    local fu=0
    local hu=0
    local mu=0
    local hsu=0
    local msu=0
    while read -r url; do
      project_name=$(get_project_name ${url})
      while IFS=, read -r sha vms vmc cmv vmf fmv vmh hmv vmm mmv vmhs hsmv vmms msmv; do
        if [ "${vmc}" -gt 0 ]; then ((cu++)); fi
        if [ "${vmf}" -gt 0 ]; then ((fu++)); fi
        if [ "${vmh}" -gt 0 ]; then ((hu++)); fi
        if [ "${vmm}" -gt 0 ]; then ((mu++)); fi
        if [ "${vmhs}" -gt 0 ]; then ((hsu++)); fi
        if [ "${vmms}" -gt 0 ]; then ((msu++)); fi
      done < <(head -n -3 "${TABLES_DIR}/${project_name}_${variant}_safety-alt.csv" | tail -n +2)
    done < "${PROJECT_LIST}"
    echo "${cu},${fu},${hu},${mu},${hsu},${msu}"
    # Unsafe projects
    cu=0
    fu=0
    hu=0
    mu=0
    hsu=0
    msu=0
    echo -n "UNSAFE_P,"
    while IFS=, read -r sha vms vmc cmv vmf fmv vmh hmv vmm mmv vmhs hsmv vmms msmv; do
      if [ "${vmc}" -gt 0 ]; then ((cu++)); fi
      if [ "${vmf}" -gt 0 ]; then ((fu++)); fi
      if [ "${vmh}" -gt 0 ]; then ((hu++)); fi
      if [ "${vmm}" -gt 0 ]; then ((mu++)); fi
      if [ "${vmhs}" -gt 0 ]; then ((hsu++)); fi
      if [ "${vmms}" -gt 0 ]; then ((msu++)); fi
    done < <(head -n -3 "${TABLES_DIR}/${variant}_safety-alt.csv" | tail -n +2)
    echo "${cu},${fu},${hu},${mu},${hsu},${msu}"
  } > "${output_file}"
}

mkdir -p "${EXPERIMENT_ROOT}/../write_result_logs"
for url in $(cat "${PROJECT_LIST}"); do
  project_name=$(get_project_name ${url})
  echo "bash ./write_results.sh ${project_name} ${INCLUDE_BASE_RV} &> ${EXPERIMENT_ROOT}/../write_result_logs/${project_name}.txt"
done > write_result_commands.txt

cat write_result_commands.txt | parallel -j "${MAX_JOBS}"

# TODO: Try use Python instead for create_inspection_file and get_three_row_table
# create_inspection_file
get_summary_safety_table "ps1c"
append_summary_rows "${TABLES_DIR}/ps1c_safety-alt.csv"
get_summary_safety_table "ps3cl"
append_summary_rows "${TABLES_DIR}/ps3cl_safety-alt.csv"
# get_three_row_table "ps1c"
# get_three_row_table "ps3cl"
