#!/bin/bash
#
# Runs the entire experiment process on a single project.
# Arguments:
#   1. Project URL.
#   2. Whether to enable statistics or not, use "stats" to enable.
#   3. The max number of iterations to run for this project.
#   4. (-s true) to continue running even if an error occurs.
SCRIPT_DIR=$( cd $( dirname $0 ) && pwd )
source ./constants.sh
source ./utils.sh

ENV_DIR=""
SILENT_ERROR=false
USE_THIRD_PARTY=false
RUN_CONFIGS=""
while getopts :e:s:l:c: opts; do
    case "${opts}" in
      e ) ENV_DIR="${OPTARG}" ;;
      s ) SILENT_ERROR="${OPTARG}" ;;
      l ) USE_THIRD_PARTY="${OPTARG}" ;;
      c ) RUN_CONFIGS="${OPTARG}" ;;
    esac
done
shift $((${OPTIND} - 1))

PROJECT_URL="$1"
STATS="$2"
MAX_NUM_REVISIONS="${3:-}"

if [[ -z ${RUN_CONFIGS} ]]; then
  RUN_CONFIGS=("test" "MOP" "VMS" "STARTS" "EKSTAZI" "FINE_STARTS" "FINE_EKSTAZI" "CLASSES_ps1c" "CLASSES_ps3cl" "FINE_ps1c" "FINE_ps3cl" "HyRTS_ps1c" "HyRTS_ps3cl" "HyRTS-S_ps1c" "HyRTS-S_ps3cl" "METHODS_ps1c" "METHODS_ps3cl" "METHODS-F_ps1c" "METHODS-F_ps3cl" "METHODS-FA_ps1c" "METHODS-FA_ps3cl" "METHODS-S_ps1c" "METHODS-S_ps3cl")
else
  IFS=',' read -ra RUN_CONFIGS <<< "${RUN_CONFIGS}"
fi

if [[ -z ${ENV_DIR} ]]; then
  ENV_DIR="${SCRIPT_DIR}/../../env"
fi

PROJECT_NAME="$(get_project_name "$PROJECT_URL")"
REVISIONS_FILE="${REVISIONS_DIR}/${PROJECT_NAME}.txt"
export LOCAL_M2_REPO=${ENV_DIR}/repo

#######################################
# Checks for input validity of the entire script.
# Will terminate the program if some inputs don't have the right type.
#######################################
function input_check {
  if ! is_url "${PROJECT_URL}"; then
    echo "${ERROR_LABEL} PROJECT_URL has to be a valid URL."
    exit 1
  fi
}

function install_starts {
  pushd ${ENV_DIR}/starts
  mvn install ${SKIPS} -DskipTests --no-transfer-progress -Dmaven.repo.local=${project_repo}
  popd
}

#######################################
# Execute the command that is essential to the experiment process.
# For instance, run eMOP on a project could be such a command.
# Globals:
#   LOCAL_M2_REPO
#   PROJECT_NAME
#   PATH
#   LOG_LABEL
#   SKIPS
# Arguments:
#   Name of the project.
#   Type of command, this should match what's specified in $ALGORITHM.
#   Path to the log file that should store the execution output.
#######################################
function execute {
  local project_name="$1"
  local command="$2"
  local log_file="$3"

  local project_repo=${LOCAL_M2_REPO}
  local project_agent=${project_repo}/javamop-agent-emop/javamop-agent-emop/1.0/javamop-agent-emop-1.0.jar
  # By convention, use "_" as the delimeter for variants.
  local variant="$(echo "$command" | rev | cut -d "_" -f 1 | rev)"
  (
    export RVMLOGGINGLEVEL=UNIQUE
    export MAVEN_OPTS="${MAVEN_OPTS} -Xmx500g -XX:-UseGCOverheadLimit"
    echo "${LOG_LABEL} PATH: ${PATH}"
    echo "${LOG_LABEL} Which Maven: $(which mvn)"
    set -o xtrace
    mvn clean
    echo "${LOG_LABEL} - Executing ${command}"
    # TODO: install agent
    if [[ ${command} != "test" && ${command} != "compile" ]]; then
      # Note: STARTS here is not just STARTS, it's RTS + RV.
      if [[ ${command} == "VMS" || ${command} == "MOP" || ${command} == "STARTS" || ${command} == "EKSTAZI" || ${command} == "FINE_STARTS" || ${command} == "FINE_EKSTAZI" ]]; then
        mvn install:install-file -Dfile=${ENV_DIR}/agents/no-track-no-stats-agent.jar -DgroupId="javamop-agent" -DartifactId="javamop-agent" -Dversion="1.0" -Dpackaging="jar" -Dmaven.repo.local=${project_repo}
      else
        mvn install:install-file -Dfile=${ENV_DIR}/agents/no-track-no-stats-agent.jar -DgroupId="javamop-agent-emop" -DartifactId="javamop-agent-emop" -Dversion="1.0" -Dpackaging="jar" -Dmaven.repo.local=${project_repo}
      fi
    fi

    local rts_part=""
    if [[ ${command} == *"+"* ]]; then
      rts_part=$(echo ${command} | cut -d "+" -f 1)
    fi
    if [[ -z ${rts_part} ]]; then
      local halt_for_rts="false"
    else
      local halt_for_rts="true"
    fi

    time (
      if [ "${command}" == "test" ]; then
        mvn test -fae ${SKIPS} -Dmaven.repo.local=${project_repo}
      elif [ "${command}" == "compile" ]; then
        mvn clean compile -fae ${SKIPS} -Dmaven.repo.local=${project_repo}
      elif [[ "${command}" == *"HyRTS-S"* ]]; then
        mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/emop-extension-1.0-SNAPSHOT.jar:${ENV_DIR}/extensions/emop-agent-extension-1.0-SNAPSHOT.jar emop:rps -DhaltForRTS=${halt_for_rts} -DuseThirdParty=${USE_THIRD_PARTY} -Dgranularity=HYBRID -DfinerSpecMapping=true $(get_variant_flags $variant) ${EMOP_STATS} ${VERBOSE_AGENT} ${DEBUG_TRUE} ${PROFILER_OPTIONS} -fae ${SKIPS} -Dmaven.repo.local=${project_repo} -DjavamopAgent=${project_agent}
      elif [[ "${command}" == *"HyRTS"* ]]; then
        mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/emop-extension-1.0-SNAPSHOT.jar:${ENV_DIR}/extensions/emop-agent-extension-1.0-SNAPSHOT.jar emop:rps -DhaltForRTS=${halt_for_rts} -DuseThirdParty=${USE_THIRD_PARTY} -Dgranularity=HYBRID $(get_variant_flags $variant) ${EMOP_STATS} ${VERBOSE_AGENT} ${DEBUG_TRUE} ${PROFILER_OPTIONS} -fae ${SKIPS} -Dmaven.repo.local=${project_repo} -DjavamopAgent=${project_agent}
      elif [[ "${command}" == *"METHODS-FA"* ]]; then
        mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/emop-extension-1.0-SNAPSHOT.jar:${ENV_DIR}/extensions/emop-agent-extension-1.0-SNAPSHOT.jar emop:rps -DhaltForRTS=${halt_for_rts} -DuseThirdParty=${USE_THIRD_PARTY} -Dgranularity=METHOD -DfinerSpecMapping=true -DfinerInstrumentation=true -DfinerInstrumentationAlt=true $(get_variant_flags $variant) ${EMOP_STATS} ${VERBOSE_AGENT} ${DEBUG_TRUE} ${PROFILER_OPTIONS} -fae ${SKIPS} -Dmaven.repo.local=${project_repo} -DjavamopAgent=${project_agent}
      elif [[ "${command}" == *"METHODS-F"* ]]; then
        mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/emop-extension-1.0-SNAPSHOT.jar:${ENV_DIR}/extensions/emop-agent-extension-1.0-SNAPSHOT.jar emop:rps -DhaltForRTS=${halt_for_rts} -DuseThirdParty=${USE_THIRD_PARTY} -Dgranularity=METHOD -DfinerSpecMapping=true -DfinerInstrumentation=true $(get_variant_flags $variant) ${EMOP_STATS} ${VERBOSE_AGENT} ${DEBUG_TRUE} ${PROFILER_OPTIONS} -fae ${SKIPS} -Dmaven.repo.local=${project_repo} -DjavamopAgent=${project_agent}
      elif [[ "${command}" == *"METHODS-S"* ]]; then
        mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/emop-extension-1.0-SNAPSHOT.jar:${ENV_DIR}/extensions/emop-agent-extension-1.0-SNAPSHOT.jar emop:rps -DhaltForRTS=${halt_for_rts} -DuseThirdParty=${USE_THIRD_PARTY} -Dgranularity=METHOD -DfinerSpecMapping=true $(get_variant_flags $variant) ${EMOP_STATS} ${VERBOSE_AGENT} ${DEBUG_TRUE} ${PROFILER_OPTIONS} -fae ${SKIPS} -Dmaven.repo.local=${project_repo} -DjavamopAgent=${project_agent}
      elif [[ "${command}" == *"METHODS"* ]]; then
        mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/emop-extension-1.0-SNAPSHOT.jar:${ENV_DIR}/extensions/emop-agent-extension-1.0-SNAPSHOT.jar emop:rps -DhaltForRTS=${halt_for_rts} -DuseThirdParty=${USE_THIRD_PARTY} -Dgranularity=METHOD $(get_variant_flags $variant) ${EMOP_STATS} ${VERBOSE_AGENT} ${DEBUG_TRUE} ${PROFILER_OPTIONS} -fae ${SKIPS} -Dmaven.repo.local=${project_repo} -DjavamopAgent=${project_agent}
      elif [[ "${command}" == *"CLASSES"* ]]; then
        mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/emop-extension-1.0-SNAPSHOT.jar:${ENV_DIR}/extensions/emop-agent-extension-1.0-SNAPSHOT.jar emop:rps -DhaltForRTS=${halt_for_rts} -DuseThirdParty=${USE_THIRD_PARTY} -e $(get_variant_flags $variant) ${EMOP_STATS} ${VERBOSE_AGENT} ${DEBUG_TRUE} ${PROFILER_OPTIONS} -fae ${SKIPS} -Dmaven.repo.local=${project_repo} -DjavamopAgent=${project_agent}
      elif [[ "${command}" == *"FINE"* && "${command}" != "FINE_STARTS" && "${command}" != "FINE_EKSTAZI" ]]; then
        # Actually enableFineRTS is the real argument in control here.
        # TODO: Make it the case such that the parameter granularity replaces its functionality.
        mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/emop-extension-1.0-SNAPSHOT.jar:${ENV_DIR}/extensions/emop-agent-extension-1.0-SNAPSHOT.jar emop:rps -DuseThirdParty=${USE_THIRD_PARTY} -Dgranularity=FINE $(get_variant_flags $variant) ${EMOP_STATS} ${VERBOSE_AGENT} ${DEBUG_TRUE} ${PROFILER_OPTIONS} -fae ${SKIPS} -Dmaven.repo.local=${project_repo} -DjavamopAgent=${project_agent}
      elif [ "${command}" == "VMS" ]; then
        mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/emop-extension-1.0-SNAPSHOT.jar:${ENV_DIR}/extensions/mop-agent-extension-1.0-SNAPSHOT.jar emop:vms -fae ${SKIPS} -Dmaven.repo.local=${project_repo} -DforceSave=true
      elif [ "${command}" == "MOP" ]; then
        mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/mop-agent-extension-1.0-SNAPSHOT.jar test -fae ${SKIPS} -Dmaven.repo.local=${project_repo}
      elif [ "${command}" == "STARTS" ]; then
        mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/mop-agent-extension-1.0-SNAPSHOT.jar edu.illinois:starts-maven-plugin:1.6-SNAPSHOT:starts -fae ${SKIPS} -Dmaven.repo.local=${project_repo}
      elif [ "${command}" == "EKSTAZI" ]; then
        export SUREFIRE_VERSION="2.14"
        mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/mop-agent-extension-1.0-SNAPSHOT.jar org.ekstazi:ekstazi-maven-plugin:5.3.0:ekstazi -fae ${SKIPS} -Dmaven.repo.local=${project_repo}
      elif [ "${command}" == "FINE_STARTS" ]; then
        mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/mop-agent-extension-1.0-SNAPSHOT.jar edu.illinois:starts-maven-plugin:1.6-SNAPSHOT:starts -DfineRTS=true -DmRTS=true -DsaveMRTS=true -fae ${SKIPS} -Dmaven.repo.local=${project_repo}
      elif [ "${command}" == "FINE_EKSTAZI" ]; then
        export SUREFIRE_VERSION="2.14"
        echo "finerts=true" > ${HOME}/.ekstazirc
        echo "mrts=true" >> ${HOME}/.ekstazirc
        mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/mop-agent-extension-1.0-SNAPSHOT.jar org.ekstazi:ekstazi-maven-plugin:5.3.1:ekstazi -fae ${SKIPS} -Dmaven.repo.local=${project_repo}
      elif [ "${command}" == "emop:clean" ]; then
        mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/emop-extension-1.0-SNAPSHOT.jar emop:clean -Dmaven.repo.local=${project_repo}
      fi
      local status=$?

      if [[ -n ${rts_part} ]]; then
        if [[ -d ".${STARTS}" ]]; then
          mv ".${STARTS}" ".${STARTS}-${command}" &> "${DEV_NULL}"
        fi
        if [[ -d ".${STARTS}-${rts_part}-${command}" ]]; then
          mv ".${STARTS}-${rts_part}-${command}" ".${STARTS}" &> "${DEV_NULL}"
        fi
        if [[ -d ".${EKSTAZI}-${rts_part}-${command}" ]]; then
          mv ".${EKSTAZI}-${rts_part}-${command}" ".${EKSTAZI}" &> "${DEV_NULL}"
        fi
        if [[ ${rts_part} == "STARTS" ]]; then
          mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/emop-agent-extension-1.0-SNAPSHOT.jar edu.illinois:starts-maven-plugin:1.6-SNAPSHOT:starts -fae ${SKIPS} -Dmaven.repo.local=${project_repo}
        elif [[ ${rts_part} == "FINE_STARTS" ]]; then
          mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/emop-agent-extension-1.0-SNAPSHOT.jar edu.illinois:starts-maven-plugin:1.6-SNAPSHOT:starts -DfineRTS=true -DmRTS=true -DsaveMRTS=true -fae ${SKIPS} -Dmaven.repo.local=${project_repo}
        elif [[ ${rts_part} == "EKSTAZI" ]]; then
          export SUREFIRE_VERSION="2.14"
          mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/emop-agent-extension-1.0-SNAPSHOT.jar org.ekstazi:ekstazi-maven-plugin:5.3.0:ekstazi -fae ${SKIPS} -Dmaven.repo.local=${project_repo}
        elif [[ ${rts_part} == "FINE_EKSTAZI" ]]; then
          export SUREFIRE_VERSION="2.14"
          mvn -Dmaven.ext.class.path=${ENV_DIR}/extensions/emop-agent-extension-1.0-SNAPSHOT.jar org.ekstazi:ekstazi-maven-plugin:5.3.1:ekstazi -fae ${SKIPS} -Dmaven.repo.local=${project_repo}
        fi
        if [[ -d ".${EKSTAZI}" ]]; then
          mv ".${EKSTAZI}" ".${EKSTAZI}-${rts_part}-${command}" &> "${DEV_NULL}"
        fi
        if [[ -d ".${STARTS}" ]]; then
          mv ".${STARTS}" ".${STARTS}-${rts_part}-${command}" &> "${DEV_NULL}"
        fi
        if [[ -d ".${STARTS}-${command}" ]]; then
          mv ".${STARTS}-${command}" ".${STARTS}" &> "${DEV_NULL}"
        fi
      fi

      unset SUREFIRE_VERSION

      set +o xtrace

      exit ${status}
    )
  ) &> ${log_file}
  local status=$?
  if [[ ${status} -ne 0 && ${SILENT_ERROR} != true ]]; then
    echo "${ERROR_LABEL} - Command ${command} failed with status ${status}"
    exit 1
  fi
}

#######################################
# Returns a set of arguments for emop execution based on the name of the variant.
# Arguments:
#   Name of the variant, assumed to be in lower case.
#######################################
function get_variant_flags {
  local variant="$1"
  local to_return=""

  if [[ "${variant}" == *"1"* ]]; then
    to_return="-DclosureOption=PS1"
  elif [[ "${variant}" == *"2"* ]]; then
    to_return="-DclosureOption=PS2"
  fi
  if [[ "${variant}" == *"c"* ]]; then
    to_return="${to_return} -DincludeNonAffected=false"
  fi
  if [[ "${variant}" == *"l"* ]]; then
    to_return="${to_return} -DincludeLibraries=false"
  fi

  echo "${to_return}"
}

function patch_aspectj() {
  if [[ ! -f ${ENV_DIR}/agents/.patched ]]; then
    pushd ${SCRIPT_DIR}/../scripts/ &> /dev/null
    bash ${SCRIPT_DIR}/../scripts/patch_agent.sh ${ENV_DIR}/agents/no-track-no-stats-agent.jar
    touch ${ENV_DIR}/agents/.patched
    popd &> /dev/null
  fi
}

#######################################
# Contains the main logic of running an experiment.
# This function first sets up the right environment for a project,
# then runs various algorithms on the project by going over revisions in each project.
#######################################
function main {
  input_check
  setup_environment_variables &> "${DEV_NULL}"
  echo "${LOG_LABEL} - Setting up environment for ${PROJECT_NAME}"
  # PATCH ASPECTJ
  patch_aspectj
  PROJECT_DATA_DIR="${DATA_GENERATED_DATA}/${PROJECT_NAME}"
  mkdir -p "${PROJECT_DATA_DIR}"
  bash ${EXPERIMENT_ROOT}/setup.sh "${ENV_DIR}" &> "${PROJECT_DATA_DIR}/setup-log.txt"
  clone_repository "${PROJECT_URL}" "${EXPERIMENT_PROJECTS_DIR}"
  if [ -z "${MAX_NUM_REVISIONS}" ]; then
    MAX_NUM_REVISIONS="$(wc -l "${REVISIONS_FILE}" | xargs | cut -d ' ' -f 1)"
  fi
  (
    cd "${EXPERIMENT_PROJECTS_DIR}/${PROJECT_NAME}"
    echo "${LOG_LABEL} - Running project ${PROJECT_NAME}"
    iteration=0
    while read -r version; do
      (( iteration++ ))
      git checkout -f "${version}" &> "${DEV_NULL}"
      treat_special "${PROJECT_NAME}"
      # Need to execute compile to avoid counting time that is used on installing dependencies.
      # test-compile may not be sufficient.
      execute "${PROJECT_NAME}" "compile" "${DEV_NULL}"
      data_path="${DATA_GENERATED_DATA}/${PROJECT_NAME}/${version}"
      mkdir -p "${data_path}"
      grep --include "*.java" -rhE "package [a-z0-9_]+(\.[a-z0-9_]+)*;" . | grep "^package" | cut -d ' ' -f 2 | sed 's/;.*//g' | sort -u > packages.txt
      mv packages.txt "${data_path}"
      for algorithm in "${RUN_CONFIGS[@]}"; do
        echo "${LOG_LABEL} --- Running ${algorithm} with version: ${version} [${iteration}/${MAX_NUM_REVISIONS}]"
        # Data collection is entirely seperated from execution
        # MAIN LOGIC BEGINS
        # Should not attempt to rename the metadata directory in the first commit of a project.
        if [[ ${iteration} != 1 ]]; then
          mv ".${STARTS}-${algorithm}" ".${STARTS}" &> "${DEV_NULL}"
          mv ".${EKSTAZI}-${algorithm}" ".${EKSTAZI}" &> "${DEV_NULL}"
        fi
        touch "NEW"
        execute "${PROJECT_NAME}" "${algorithm}" "${data_path}/${algorithm}-log.txt"
        if [ -n "$(find . -newer NEW | grep violation-counts)" ]; then
          mv "${VIOLATION_COUNTS}" "${data_path}/${algorithm}-${VIOLATION_COUNTS}"
        elif [ "${algorithm}" != "test" ] && [ "${algorithm}" != "STARTS" ]; then
          echo "${WARNING_LABEL} Algorithm ${algorithm} did not produce/update ${VIOLATION_COUNTS} file."
        fi
        if [ -d ".${STARTS}" ]; then
          cp -r ".${STARTS}" "${data_path}/${STARTS}-${algorithm}"
        elif [ "${algorithm}" != "test" ] && [ "${algorithm}" != "STARTS" ] && [ "${algorithm}" != "MOP" ]; then
          echo "${WARNING_LABEL} Algorithm ${algorithm} did not produce .${STARTS} directory."
        fi
        if [ -f "profile.jfr" ]; then
          mv "profile.jfr" "${data_path}/profile-${algorithm}.jfr"
        fi
        mv ".${STARTS}" ".${STARTS}-${algorithm}" &> "${DEV_NULL}"
        mv ".${EKSTAZI}" ".${EKSTAZI}-${algorithm}" &> "${DEV_NULL}"
        # MAIN LOGIC ENDS
      done
    done < <(grep -v ^# "${REVISIONS_FILE}" | head -"${MAX_NUM_REVISIONS}")
  )
}

main
