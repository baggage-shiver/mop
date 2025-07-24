#!/bin/bash
#
# Contains constants that will be used in other scripts.

# Paths
EXPERIMENT_ROOT="$(cd $(dirname "$0") && pwd)"
EXPERIMENT_PROJECTS_DIR="${EXPERIMENT_ROOT}/../experiment-projects"
DATA_GENERATED_DATA="${EXPERIMENT_ROOT}/../data/generated-data"
REVISIONS_DIR="${EXPERIMENT_ROOT}/../data/revisions"
DEV_NULL="/dev/null"
TABLES_DIR="${EXPERIMENT_ROOT}/../tables"
PLOTS_DIR="${EXPERIMENT_ROOT}/../plots"
LOGS_DIR="${EXPERIMENT_ROOT}/../logs"
LOCAL_DEPENDENCIES_DIR="${EXPERIMENT_ROOT}/../local_dependencies"
SAVED_LOGS_DIR="${EXPERIMENT_ROOT}/saved-logs"
INSPECTION_FILE="${TABLES_DIR}/inspection.csv"
VIOLATIONS="${DATA_GENERATED_DATA}/violations"

# Texts
SPEC_LIST_NAME="classToSpecs.txt"
STARTS="starts"
LOG_FILE_NAME="-log.txt"
IMPACTED_CLASSES_ENTRY="ImpactedClasses:"
IMPACTED_METHODS_ENTRY="ImpactedMethods:"
AFFECTED_SPECS_ENTRY="AffectedSpecs:"
VIOLATION_COUNTS="violation-counts"
SUMMARY="summary"
DOCKER_IMG_NAME="finemop:latest"
MVN_VERSION="3.3.9"

# Flags
SKIPS="-Dcheckstyle.skip -Drat.skip -Denforcer.skip -Danimal.sniffer.skip -Dmaven.javadoc.skip -Dfindbugs.skip -Dwarbucks.skip -Dmodernizer.skip -Dimpsort.skip -Dpmd.skip -Dxjc.skip -Djacoco.skip -Dinvoker.skip -DskipDocs -DskipITs -Dbuildhelper.uptodateproperty.skip -Dbuildhelper.uptodateproperties.skip"
PROFILER_OPTIONS="-Dprofile -DprofileFormat=CONSOLE"
EMOP_STATS="-DenableStats=false"
VERBOSE_AGENT="-DverboseAgent=false"
DEBUG_TRUE="-Ddebug=false"
ARGLINE_AGENTPATH_KEY="-DargLine=-agentpath:"
ARGLINE_AGENTPATH_VAL="/async-profiler-3.0-linux-x64/lib/libasyncProfiler.so=start,file=profile.jfr"

# Labels
LOG_LABEL="[HYBRID-EMOP EXPERIMENT]"
WARNING_LABEL="[WARNING]"
ERROR_LABEL="[ERROR]"

# Configs
CLASSIC_CONFIG=("test" "HyRTS" "METHODS" "CLASSES" "FINE" "VMS" "MOP")
ABLATION_STUDY_CONFIG_PS1C=("test" "VMS" "CLASSES_ps1c" "FINE_ps1c" "HyRTS_ps1c" "HyRTS-S_ps1c" "METHODS_ps1c" "METHODS-S_ps1c")
ABLATION_STUDY_CONFIG_PS3CL=("test" "VMS" "CLASSES_ps3cl" "FINE_ps3cl" "HyRTS_ps3cl" "HyRTS-S_ps3cl" "METHODS_ps3cl" "METHODS-S_ps3cl")
EVAL_FULL_CONFIG=("test" "MOP" "VMS" "STARTS" "CLASSES_ps1c" "CLASSES_ps3cl" "FINE_ps1c" "FINE_ps3cl" "HyRTS_ps1c" "HyRTS_ps3cl" "HyRTS-S_ps1c" "HyRTS-S_ps3cl" "METHODS_ps1c" "METHODS_ps3cl" "METHODS-F_ps1c" "METHODS-F_ps3cl" "METHODS-FA_ps1c" "METHODS-FA_ps3cl" "METHODS-S_ps1c" "METHODS-S_ps3cl")
TMP_RTS_TEST_CONFIG=("test" "VMS" "STARTS" "CLASSES_ps1c" "METHODS-S_ps3cl")
FINER_INSTRUMENTATION_CONFIG=("test" "VMS" "CLASSES_ps1c" "METHODS-S_ps1c" "METHODS-F_ps1c" "METHODS-FA_ps1c" "METHODS_ps1c")
#MINIMAL_CONFIG=("test" "VMS" "CLASSES_ps1c" "METHODS_ps1c" "METHODS-S_ps1c")
MINIMAL_CONFIG=("test" "VMS" "CLASSES_ps3cl" "METHODS_ps3cl" "METHODS-S_ps3cl")
#ALGORITHMS=(${FINER_INSTRUMENTATION_CONFIG[@]})
ALGORITHMS=(${EVAL_FULL_CONFIG[@]})
EMOP_ALGORITHMS=("HyRTS" "METHODS" "CLASSES" "FINE")

# Column indexes (starting at 1)
SHA_INDEX=1
EXECUTION_TIME_INDEX=3
VIOLATION_COUNTS_INDEX=7

# Numerical
TRUE=1
FALSE=0
ROUNDING=3

# Inspection categories
HYRTS_UNSAFE=1
METHODS_UNSAFE=2
CLASSES_UNSAFE=3
NON_DETERMINISM=4
HYRTS_CLASSES_IMPRECISE=5
METHODS_HYRTS_IMPRECISE=6
METHODS_CLASSES_IMPRECISE=7
FINE_UNSAFE=8
