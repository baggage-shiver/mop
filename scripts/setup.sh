#!/bin/bash
#
# Sets up the environment for running JavaMOP/eMOP on a particular project.
# Arguments:
#   Project name.
#   Whether to include statistics or not. Use "stats" to enable statistics.
#   Whether to use profiler or not. Use "ON" to turn on, and "OFF" to turn off.
SCRIPT_DIR=$( cd $( dirname $0 ) && pwd )
source ./constants.sh
source ./utils.sh

ENV_DIR="$1"
USE_PROFILER=${2:-"OFF"}


#######################################
# Setup profiler option for the extension.
#######################################
function setup_profiler {
  if [[ ${USE_PROFILER} == "OFF" ]]; then
    return
  fi

  if [[ $(arch) == "x86_64" ]]; then
    export PROFILER_OPTION="-agentpath:${ENV_DIR}/async-profiler-2.9-linux-x64/lib/libasyncProfiler.so=start,interval=5ms,event=wall,file=profile.jfr"
  elif [[ $(arch) == "aarch64" || $(arch) == "arm64" ]]; then
    export PROFILER_OPTION="-agentpath:${ENV_DIR}/async-profiler-2.9-linux-x64/lib/libasyncProfiler.so=start,interval=5ms,event=wall,file=profile.jfr"
  fi
}

function setup_environment {
  echo "Setting up environment in ${ENV_DIR}"

  # Install profiler
  mkdir -p ${ENV_DIR}
  if [[ ! -d ${ENV_DIR}/async-profiler-2.9-linux-x64 ]]; then
    # TODO: macOS
    cd ${ENV_DIR}
    wget https://github.com/async-profiler/async-profiler/releases/download/v2.9/async-profiler-2.9-linux-x64.tar.gz
    tar xf async-profiler-2.9-linux-x64.tar.gz
    rm async-profiler-2.9-linux-x64.tar.gz
  fi

  # Install TraceMOP agent
  mkdir -p ${ENV_DIR}/agents

  if [[ ! -d ${ENV_DIR}/tracemop ]]; then
    cd ${ENV_DIR}
    git clone https://github.com/SoftEngResearch/tracemop

    cd ${ENV_DIR}/tracemop/scripts
    bash install.sh false true
    mv no-track-stats-agent.jar ${ENV_DIR}/agents

    cd ${ENV_DIR}/tracemop/scripts
    cp ${SCRIPT_DIR}/BaseAspect_new.aj .
    bash install.sh false false
    mv no-track-no-stats-agent.jar ${ENV_DIR}/agents
  fi

  # Install Ekstazi, STARTS, and eMOP
  if [[ ! -d ${ENV_DIR}/ekstazi ]]; then
    cd ${ENV_DIR}
    git clone https://github.com/gliga/ekstazi
    cd ekstazi
    mvn install -Dcheckstyle.skip -Drat.skip -Denforcer.skip -Danimal.sniffer.skip -Dmaven.javadoc.skip -Dfindbugs.skip -Dwarbucks.skip -Dmodernizer.skip -Dimpsort.skip -Dpmd.skip -Dxjc.skip -Djacoco.skip -Dinvoker.skip -DskipDocs -DskipITs -Dbuildhelper.uptodateproperty.skip -Dbuildhelper.uptodateproperties.skip -DskipTests --no-transfer-progress -Dmaven.repo.local=${ENV_DIR}/repo
  fi
  
  if [[ ! -d ${ENV_DIR}/fine-ekstazi ]]; then
    cd ${ENV_DIR}
    git clone https://github.com/EngineeringSoftware/fine-ekstazi
    cd fine-ekstazi
    mvn install -Dcheckstyle.skip -Drat.skip -Denforcer.skip -Danimal.sniffer.skip -Dmaven.javadoc.skip -Dfindbugs.skip -Dwarbucks.skip -Dmodernizer.skip -Dimpsort.skip -Dpmd.skip -Dxjc.skip -Djacoco.skip -Dinvoker.skip -DskipDocs -DskipITs -Dbuildhelper.uptodateproperty.skip -Dbuildhelper.uptodateproperties.skip -DskipTests --no-transfer-progress -Dmaven.repo.local=${ENV_DIR}/repo
  fi

  if [[ ! -d ${ENV_DIR}/starts ]]; then
    cd ${ENV_DIR}
    cp -r ${SCRIPT_DIR}/../starts starts
    cd starts
    mvn install -Dcheckstyle.skip -Drat.skip -Denforcer.skip -Danimal.sniffer.skip -Dmaven.javadoc.skip -Dfindbugs.skip -Dwarbucks.skip -Dmodernizer.skip -Dimpsort.skip -Dpmd.skip -Dxjc.skip -Djacoco.skip -Dinvoker.skip -DskipDocs -DskipITs -Dbuildhelper.uptodateproperty.skip -Dbuildhelper.uptodateproperties.skip -Dcheckstyle.skip -DskipTests --no-transfer-progress -Dmaven.repo.local=${ENV_DIR}/repo
  fi

  if [[ ! -d ${ENV_DIR}/finemop ]]; then
    cd ${ENV_DIR}
    cp -r ${SCRIPT_DIR}/../finemop finemop
    cd finemop
    mvn install -Dcheckstyle.skip -Drat.skip -Denforcer.skip -Danimal.sniffer.skip -Dmaven.javadoc.skip -Dfindbugs.skip -Dwarbucks.skip -Dmodernizer.skip -Dimpsort.skip -Dpmd.skip -Dxjc.skip -Djacoco.skip -Dinvoker.skip -DskipDocs -DskipITs -Dbuildhelper.uptodateproperty.skip -Dbuildhelper.uptodateproperties.skip -Dcheckstyle.skip -DskipTests --no-transfer-progress -Dmaven.repo.local=${ENV_DIR}/repo
  fi
}

function setup_repo {
  # TODO: stats agent if needed
  mvn install:install-file -Dfile=${ENV_DIR}/agents/no-track-no-stats-agent.jar -DgroupId="javamop-agent" -DartifactId="javamop-agent" -Dversion="1.0" -Dpackaging="jar" -Dmaven.repo.local=${ENV_DIR}/repo
  mvn install:install-file -Dfile=${ENV_DIR}/agents/no-track-no-stats-agent.jar -DgroupId="javamop-agent-emop" -DartifactId="javamop-agent-emop" -Dversion="1.0" -Dpackaging="jar" -Dmaven.repo.local=${ENV_DIR}/repo
}

function setup_extensions {
  mkdir -p ${ENV_DIR}/extensions

  if [[ ! -f ${ENV_DIR}/extensions/mop-agent-extension-1.0-SNAPSHOT.jar ]]; then
    pushd ${SCRIPT_DIR}/../local_dependencies/mop-agent-extension
    mvn package

    cp target/mop-agent-extension-1.0-SNAPSHOT.jar ${ENV_DIR}/extensions
    popd
  fi

  if [[ ! -f ${ENV_DIR}/extensions/starts-extension-1.0-SNAPSHOT.jar ]]; then
    pushd ${SCRIPT_DIR}/../local_dependencies/starts-extension
    mvn package

    cp target/starts-extension-1.0-SNAPSHOT.jar ${ENV_DIR}/extensions
    popd
  fi

  if [[ ! -f ${ENV_DIR}/extensions/emop-extension-1.0-SNAPSHOT.jar ]]; then
    pushd ${SCRIPT_DIR}/../local_dependencies/emop-extension
    mvn package

    cp target/emop-extension-1.0-SNAPSHOT.jar ${ENV_DIR}/extensions
    popd
  fi

  if [[ ! -f ${ENV_DIR}/extensions/emop-agent-extension-1.0-SNAPSHOT.jar ]]; then
    pushd ${SCRIPT_DIR}/../local_dependencies/emop-agent-extension
    mvn package

    cp target/emop-agent-extension-1.0-SNAPSHOT.jar ${ENV_DIR}/extensions
    popd
  fi
}


setup_profiler
setup_environment
setup_repo
setup_extensions
