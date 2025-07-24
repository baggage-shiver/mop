#!/bin/bash
#
# Run a single project's experiment with Docker.
# Prerequisite: Relies on the Docker image finemop:latest

source ./constants.sh
source ./utils.sh

USE_THIRD_PARTY=false
while getopts :i:l: opts; do
    case "${opts}" in
      i ) IMAGE_NAME="${OPTARG}" ;;
      l ) USE_THIRD_PARTY="${OPTARG}" ;;
    esac
done
shift $((${OPTIND} - 1))

PROJECT_URL=$1
STATS=$2
MAX_REVS=$3
RUN_CONFIGS=$4

function run_single_with_docker {
    local project_url=$1
    local stats=$2
    local max_revs=$3

    local project_name="$(get_project_name "${project_url}")"

    if [[ -n ${IMAGE_NAME} ]]; then
        DOCKER_IMG_NAME="${IMAGE_NAME}"
    fi
    mkdir -p "${DATA_GENERATED_DATA}"

    # Start a Docker container with project name as its given name and save id.
    local id="$(docker run -itd --name "${project_name}" "${DOCKER_IMG_NAME}")"
    # Install starts.
    docker exec -w /root/env/starts -e PATH=/root/apache-maven/bin:/usr/lib/jvm/java-8-openjdk/bin:/root/aspectj-1.9.7/bin:/root/aspectj-1.9.7/lib/aspectjweaver.jar:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin -e JAVA_HOME=/usr/lib/jvm/java-8-openjdk -e CLASSPATH=/root/aspectj-1.9.7/lib/aspectjtools.jar:/root/aspectj-1.9.7/lib/aspectjrt.jar:/root/aspectj-1.9.7/lib/aspectjweaver.jar: "${id}" mvn install -Dcheckstyle.skip -Drat.skip -Denforcer.skip -Danimal.sniffer.skip -Dmaven.javadoc.skip -Dfindbugs.skip -Dwarbucks.skip -Dmodernizer.skip -Dimpsort.skip -Dpmd.skip -Dxjc.skip -Djacoco.skip -Dinvoker.skip -DskipDocs -DskipITs -Dbuildhelper.uptodateproperty.skip -Dbuildhelper.uptodateproperties.skip -Dcheckstyle.skip -DskipTests --no-transfer-progress -Dmaven.repo.local=/root/env/repo &>> "${LOGS_DIR}/${project_name}.txt"
    # Install emop and finemop.
    docker exec -w /root/env/emop -e PATH=/root/apache-maven/bin:/usr/lib/jvm/java-8-openjdk/bin:/root/aspectj-1.9.7/bin:/root/aspectj-1.9.7/lib/aspectjweaver.jar:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin -e JAVA_HOME=/usr/lib/jvm/java-8-openjdk -e CLASSPATH=/root/aspectj-1.9.7/lib/aspectjtools.jar:/root/aspectj-1.9.7/lib/aspectjrt.jar:/root/aspectj-1.9.7/lib/aspectjweaver.jar: "${id}" mvn install -Dcheckstyle.skip -Drat.skip -Denforcer.skip -Danimal.sniffer.skip -Dmaven.javadoc.skip -Dfindbugs.skip -Dwarbucks.skip -Dmodernizer.skip -Dimpsort.skip -Dpmd.skip -Dxjc.skip -Djacoco.skip -Dinvoker.skip -DskipDocs -DskipITs -Dbuildhelper.uptodateproperty.skip -Dbuildhelper.uptodateproperties.skip -Dcheckstyle.skip -DskipTests --no-transfer-progress -Dmaven.repo.local=/root/env/repo &>> "${LOGS_DIR}/${project_name}.txt"
    if [[ -n ${RUN_CONFIGS} ]]; then
      # Docker command specifying environment variables and script to run.
      docker exec -w /root/finemop/scripts -e PATH=/root/apache-maven/bin:/usr/lib/jvm/java-8-openjdk/bin:/root/aspectj-1.9.7/bin:/root/aspectj-1.9.7/lib/aspectjweaver.jar:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin -e JAVA_HOME=/usr/lib/jvm/java-8-openjdk -e CLASSPATH=/root/aspectj-1.9.7/lib/aspectjtools.jar:/root/aspectj-1.9.7/lib/aspectjrt.jar:/root/aspectj-1.9.7/lib/aspectjweaver.jar: "${id}" bash run_experiment.sh -c "${RUN_CONFIGS}" -l "${USE_THIRD_PARTY}" "${project_url}" "${stats}" "${max_revs}" >> "${LOGS_DIR}/${project_name}.txt" 2>&1
    else
      # Docker command specifying environment variables and script to run.
      docker exec -w /root/finemop/scripts -e PATH=/root/apache-maven/bin:/usr/lib/jvm/java-8-openjdk/bin:/root/aspectj-1.9.7/bin:/root/aspectj-1.9.7/lib/aspectjweaver.jar:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin -e JAVA_HOME=/usr/lib/jvm/java-8-openjdk -e CLASSPATH=/root/aspectj-1.9.7/lib/aspectjtools.jar:/root/aspectj-1.9.7/lib/aspectjrt.jar:/root/aspectj-1.9.7/lib/aspectjweaver.jar: "${id}" bash run_experiment.sh -l "${USE_THIRD_PARTY}" "${project_url}" "${stats}" "${max_revs}" >> "${LOGS_DIR}/${project_name}.txt" 2>&1
    fi
    docker cp "${id}:/root/finemop/data/generated-data/${project_name}" "${DATA_GENERATED_DATA}"
    # Remove Docker container at the end.
    docker rm -f "${id}"
}

mkdir -p "${LOGS_DIR}"
run_single_with_docker "${PROJECT_URL}" "${STATS}" "${MAX_REVS}"
