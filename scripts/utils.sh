#!/bin/bash
#
# Obtain information about a project, using only GitHub URL.

source ./constants.sh

#######################################
# Append summary rows (sum, arithmetic mean, geometric mean) to a table.
# Arguments:
#   Input table file (also the output table file).
#######################################
function append_summary_rows {
  local file="$1"

  sum_row="sum"
  arith_mean_row="arith_mean"
  geo_mean_row="geo_mean"

  for col in $(seq 2 $(echo "$(head -1 "${file}" | tr -cd , | wc -c | xargs) + 1" | bc -l)); do
    sum="$(cut -d ',' -f ${col} "${file}" | tail -n +2 |  awk '{ print $1 }' | paste -sd+ - | bc -l)"
    arith_mean="$(cut -d ',' -f ${col} "${file}" | tail -n +2 | awk '$1>0{tot+=$1; c++} END {m=tot/c; printf "%.2f\n", m}' 2> ${DEV_NULL})"
    if [ -z "${arith_mean}" ]; then
      arith_mean=0
    fi
    geo_mean="$(cut -d ',' -f ${col} "${file}" | tail -n +2 | awk 'BEGIN{E = exp(1);} $1>0{tot+=log($1); c++} END{m=tot/c; printf "%.2f\n", E^m}' 2> ${DEV_NULL})"
    if [ -z "${geo_mean}" ]; then
      geo_mean=0
    fi
    sum_row="${sum_row},${sum}"
    arith_mean_row="${arith_mean_row},${arith_mean}"
    geo_mean_row="${geo_mean_row},${geo_mean}"
  done
  
  echo "${sum_row}" >> "${file}"
  echo "${arith_mean_row}" >> "${file}"
  echo "${geo_mean_row}" >> "${file}"
}

#######################################
# Obtain discrete statistics including number of monitors and number of events.
# Arguments:
#   Name of the project.
#   A commit hash (SHA) of that project.
#   The algorithm to choose from, as specified by ALGORITHMS in constants.sh.
#   Which discrete statistics to collect, possible values:
#     - monitor: the number of monitors.
#     - event: the number of events.
#     - impacted_classes: the number of impacted classes.
#######################################
function get_count {
  local project=$1
  local commit=$2
  local algorithm=$3
  local option=$4

  local log="${DATA_GENERATED_DATA}/${project}/${commit}/${algorithm}${LOG_FILE_NAME}"
  if [ -f "${log}" ]; then
    if [[ "$option" = "monitor" ]]; then
      value="$(grep -a '#monitors:' "${log}" | awk '{ print $2 }' | paste -sd+ - | bc | tr -d "\n")"
    elif [[ "$option" = "event" ]]; then
      value="$(grep -a '#event' "${log}" | awk '{ print $4 }' | paste -sd+ - | bc | tr -d "\n")"
    elif [[ "$option" = "total_classes" ]]; then
      value="$(grep -a "Total number of classes" "${log}" | cut -d ' ' -f 7)"
    elif [[ "$option" = "impacted_classes" ]]; then
      # value="$(grep "Number of impacted classes" ${log} | cut -d ' ' -f 7)"
      if [[ -n "$(grep -a "Total ImpactedClasses" "${log}")" ]]; then
        value="$(grep -a "Total ImpactedClasses" "${log}" | cut -d ' ' -f 4)"
      else
        value="$(grep -a "ImpactedClasses" "${log}" | grep -v ']$' | cut -d ' ' -f 3)"
      fi
    elif [[ "$option" = "impacted_methods" ]]; then
      if [[ -n "$(grep -a "Total ImpactedMethods" "${log}")" ]]; then
        value="$(grep -a "Total ImpactedMethods" "${log}" | cut -d ' ' -f 4)"
      else
        value="$(grep -a "ImpactedMethods" "${log}" | grep -v ']$' | cut -d ' ' -f 3)"
      fi
      if [[ "${algorithm}" == "VMS" ]] || [[ "${algorithm}" == "CLASSES"* ]] || [[ "${algorithm}" == "FINE"* ]]; then
        (
          cd "${EXPERIMENT_PROJECTS_DIR}/${project}"
          git checkout -f ${commit}
        ) &> /dev/null
        value="$(java -jar "${EXPERIMENT_ROOT}/local_dependencies/impacted-method-estimator/target/impacted-method-estimator-1.0-SNAPSHOT-jar-with-dependencies.jar" "${EXPERIMENT_PROJECTS_DIR}/${project}" "${DATA_GENERATED_DATA}/${project}/${commit}/starts-${algorithm}/impacted-classes")"
      fi
    elif [[ "$option" = "affected_specs" ]]; then
      value="$(grep -a "AffectedSpecs: " "${log}" | grep -v ']$' | cut -d ' ' -f 3)"
    fi
    if [[ -z "${value}" ]]; then value="0"; fi
    echo -n "${value}"
  fi
}

#######################################
# Calculates the time in second that a specific process in the experiment run takes.
# Arguments:
#   Name of the project.
#   A commit hash (SHA) of that project.
#   The algorithm to choose from, as specified by ALGORITHMS in constants.sh.
#   Which segment in runtime breakdown, possible values:
#     - total: total runtime of the algorithm
#     - analysis: analysis time of the algorithm, this includes:
#       - time to execute Impacted*Mojo, where * could be Class, Methods, Hybrid.
#       - time to complete compile-time weaving.
#       - time to compute affected specs.
#       - time to write affected specs to disk is tiny, but it is counted toward this category.
#     - monitor: time of monitoring the code and run the tests.
#######################################
function get_time_s {
  local project=$1
  local commit=$2
  local algorithm=$3
  local option=$4
  
  local file="${DATA_GENERATED_DATA}/${project}/${commit}/${algorithm}${LOG_FILE_NAME}"
  if [ -f "${file}" ]; then
    if [[ "$option" = "total" ]]; then
      local t=$(tail ${file} | grep ^real | cut -f2 )
      min=$(echo ${t} | cut -dm -f1)
      sec=$(echo ${t} | cut -d\. -f1 | cut -dm -f2)
      frac=$(echo ${t} | cut -d. -f2 | tr -d 's')
      time=$(echo "scale=3;(${min} * 60)+${sec}+(${frac}/1000)" | bc -l)
      local fail=$(grep "BUILD FAILURE" ${file})
      if [ ! -z "${fail}" ]; then time="-"${time}; fi
      echo -n "${time}"
    elif [[ "$option" = "analysis" ]]; then
      local cia_ms=$(grep "Execute Impacted.* Mojo takes" ${file} | cut -d ' ' -f 8)
      local weaving_ms=$(grep "Compile-time weaving takes" ${file} | cut -d ' ' -f 7)
      local affected_specs_ms=$(grep "Compute affected specs takes" ${file} | cut -d ' ' -f 8)
      local write_1=$(grep "Write affected specs to disk takes" ${file} | cut -d ' ' -f 10)
      local write_2=$(grep "Generating aop-ajc.xml and replace it takes" ${file} | cut -d ' ' -f 10)
      if [[ -z "$cia_ms" ]]; then cia_ms=0; fi
      if [[ -z "$weaving_ms" ]]; then weaving_ms=0; fi
      if [[ -z "$affected_specs_ms" ]]; then affected_specs_ms=0; fi
      if [[ -z "$write_1" ]]; then write_1=0; fi
      if [[ -z "$write_2" ]]; then write_2=0; fi
      echo -n "$(echo "scale=3;($cia_ms + $weaving_ms + $affected_specs_ms + $write_1 + $write_2) * 1.0 / 1000" | bc -l)"
    elif [[ "$option" = "monitor" ]]; then
      local raw=$(grep "test {execution: default-test}" ${file} | tr -s ' ' | cut -d ' ' -f "6,7")
      local value=$(echo ${raw} | cut -d ' ' -f 1)
      local unit=$(echo ${raw} | cut -d ' ' -f 2)
      if [[ "$unit" = "s" ]]; then
        echo -n "$value"
      elif [[ "$unit" = "ms" ]]; then
        echo -n "$(echo "scale=3;$value * 1.0 / 1000" | bc -l)"
      fi
    fi
  else
    echo -n "0"
  fi
}

#######################################
# Checks whether the given input is a valid number or not.
# Arguments:
#   The input to check.
# Outputs:
#   Whether the input is a valid number or not.
# References:
#   https://stackoverflow.com/questions/5431909/returning-a-boolean-from-a-bash-function
#######################################
function is_number {
  local to_check="$1"
  local re='^[0-9]+$'
  if [[ "${to_check}" =~ "${re}" ]]; then
    return "${TRUE}"
  else
    return "${FALSE}"
  fi
}

#######################################
# Checks whether the given input is a valid URL or not.
# Arguments:
#   The input to check.
# Outputs:
#   Whether the input is a valid URL or not.
# References:
#   https://stackoverflow.com/questions/3183444/check-for-valid-link-url
#######################################
function is_url {
  local to_check="$1"
  local re='(https?|ftp|file)://[-[:alnum:]\+&@#/%?=~_|!:,.;]*[-[:alnum:]\+&@#/%=~_|]'
  if [[ "${to_check}" =~ "${re}" ]]; then
    return "${TRUE}"
  else
    return "${FALSE}"
  fi
}

#######################################
# Obtains the project name from GitHub URL.
# Arguments:
#   GitHub url of the project, should be in the form of: "https://github.com/<owner>/<project_name>"
# Outputs:
#   Outputs the project name to stdout, will be in the format of "<project_name>".
#######################################
function get_project_name {
  local url="$1"
  echo "${url}" | cut -d '/' -f 5
}

#######################################
# Clones the repo at url to a destination. Will replace the local repo that is already cloned.
# Arguments:
#   GitHub url of the project.
#   Absolute path of the destination directory, will be created if it does not exist.
#######################################
function clone_repository {
  local url="$1"
  local dest="$2"
  local name="$(get_project_name "${url}")"

  echo "${LOG_LABEL} - Cloning ${name}"
  mkdir -p "${dest}"
  (
    cd "${dest}"
    rm -rf "${name}"
    git clone "${url}"
  ) &> "${DEV_NULL}"
}

#######################################
# Sets up the environment variables that will be used.
# Globals:
#   ENV_DIR
#######################################
function setup_environment_variables {
  export ASPECTJ_HOME=${ENV_DIR}/aspectj1.8
  export CLASSPATH=$ASPECTJ_HOME/lib/aspectjtools.jar:$ASPECTJ_HOME/lib/aspectjrt.jar:$ASPECTJ_HOME/lib/aspectjweaver.jar:${ENV_DIR}/tracemop/rv-monitor/target/release/rv-monitor/lib/rv-monitor-rt.jar:${ENV_DIR}/tracemop/rv-monitor/target/release/rv-monitor/lib/rv-monitor.jar:${CLASSPATH}
  export PATH=$ASPECTJ_HOME/bin:${ASPECTJ_HOME}/lib/aspectjweaver.jar:${ENV_DIR}/tracemop/javamop/bin:${ENV_DIR}/tracemop/rv-monitor/bin:${ENV_DIR}/tracemop/rv-monitor/target/release/rv-monitor/lib/rv-monitor-rt.jar:${ENV_DIR}/tracemop/rv-monitor/target/release/rv-monitor/lib/rv-monitor.jar:${PATH}
  echo "NEW ASPECTJ_HOME: ${ASPECTJ_HOME}"
  echo "NEW CLASSPATH: ${CLASSPATH}"
  echo "NEW PATH: ${PATH}"
}

#######################################
# Some projects cannot work right out of the box.
# This function preprocesses certain specified projects.
# Arguments:
#   Name of the project.
#######################################
function treat_special() {
  local p_name=$1
  echo "${LOG_LABEL} -- input to treat_special: ${p_name}"
  if [ ${p_name} == "commons-dbcp" ]; then
    find . -name TestManagedDataSourceInTx.java | xargs rm -f
    find . -name TestDriverAdapterCPDS.java | xargs rm -f
    find . -name TestAbandonedBasicDataSource.java | xargs rm -f
    find . -name TestPerUserPoolDataSource.java | xargs rm -f
  elif [ ${p_name} == "commons-imaging" ]; then
    find . -name ByteSourceImageTest.java | xargs rm -f
    find . -name BitmapRoundtripTest.java | xargs rm -f
    find . -name GrayscaleRountripTest.java | xargs rm -f
    find . -name LimitedColorRoundtripTest.java | xargs rm -f
  elif [ ${p_name} == "commons-io" ]; then
    find . -name ValidatingObjectInputStreamTest.java | xargs rm -f
    find . -name FileCleaningTrackerTestCase.java | xargs rm -f;
    find . -name FileCleanerTestCase.java | xargs rm -f
    sed -i 's/Xmx25M/Xmx8000M/' pom.xml
  elif [ ${p_name} == "commons-lang" ]; then
    find . -name FastDateFormatTest.java | xargs rm -f
    find . -name EventListenerSupportTest.java | xargs rm -f
    find . -name EventUtilsTest.java | xargs rm -f
    find . -name StrTokenizerTest.java | xargs rm -f
  elif [ "${p_name}" == "commons-math" ]; then
    find . -name LogNormalDistributionTest.java | xargs rm -f
    find . -name ChiSquaredDistributionTest.java | xargs rm -f
    find . -name NakagamiDistributionTest.java | xargs rm -f
    find . -name LegendreHighPrecisionParametricTest.java | xargs rm -f
    find . -name LegendreParametricTest.java | xargs rm -f
    find . -name BaseRuleFactoryTest.java | xargs rm -f
    find . -name KohonenTrainingTaskTest.java | xargs rm -f
    find . -name HermiteParametricTest.java | xargs rm -f
    find . -name FirstMomentTest.java | xargs rm -f
    find . -name RandomUtilsDataGeneratorJDKSecureRandomTest.java | xargs rm -f
    find . -name KendallsCorrelationTest.java | xargs rm -f
    # find . -name KolmogorovSmirnovTestTest.java | xargs rm -f  # method testTwoSampleProductSizeOverflow times out after 5s with MOP, but some other class depends on it
    find . -name Providers32ParametricTest.java | xargs rm -f # java.lang.NoClassDefFoundError: Could not initialize class org.apache.commons.math4.rng.ProvidersList
    find . -name Providers64ParametricTest.java | xargs rm -f # java.lang.NoClassDefFoundError: Could not initialize class org.apache.commons.math4.rng.ProvidersList
    find . -name ProvidersCommonParametricTest.java | xargs rm -f # java.lang.NoClassDefFoundError: Could not initialize class org.apache.commons.math4.rng.ProvidersList
    find . -name FastMathTest.java | xargs rm -f # method testPowAllSpecialCases times out after 20s with MOP
    # sed -i '0,|<configuration>|s||<configuration><forkCount>0</forkCount>|' pom.xml
  elif [ ${p_name} == "datasketches-java" ]; then
    find . -name CrossCheckQuantilesTest.java | xargs rm -f
  elif [ ${p_name} == "geoserver-manager" ]; then
    find . -name GSLayerEncoder21Test.java | xargs rm -f
  elif [ ${p_name} == "imglib2" ]; then
    git checkout pom.xml
	  cp pom.xml pom.xml.bak
	  head -n -1 pom.xml.bak > pom.xml
	  echo "	<build>
	<plugins>
	  <plugin>
	    <groupId>org.apache.maven.plugins</groupId>
	    <artifactId>maven-surefire-plugin</artifactId>
	    <version>2.22.1</version>
	    <configuration>
	      <argLine>-Xms20g -Xmx20g</argLine>
	    </configuration>
	  </plugin>
	</plugins>
	</build>
" >> pom.xml
	  tail -1 pom.xml.bak >> pom.xml
  elif [ ${p_name} == "infomas-asl" ]; then
    find . -name AnnotationDetectorTest.java | xargs rm -f
    find . -name FileIteratorTest.java | xargs rm -f
  elif [ "${p_name}" == "jackson-core" ]; then
	  sed -i 's|<artifactId>junit</artifactId>|<artifactId>junit</artifactId><version>4.13.2</version>|g' pom.xml
  elif [ ${p_name} == "jackson-databind" ]; then
    find . -name TestTypeFactoryWithClassLoader.java | xargs rm -f
  elif [ ${p_name} == "jgroups-aws" ]; then
    find . -name PortRangeTest.java | xargs rm -f
  elif [ ${p_name} == "joda-time" ]; then
    find . -name TestDateTimeComparator.java | xargs rm -f
    find . -name TestAll.java | xargs sed -i.tmp 's/.*TestDateTimeComparator.*//g'
    find . -name TestAll.java.tmp | xargs rm -f
  elif [ ${p_name} == "model-citizen" ]; then
    find . -name SkipReferenceFieldPolicyTest.java | xargs rm -f
    find . -name MappedSingletonPolicyTest.java | xargs rm -f
  elif [ "${p_name}" == "mp3agic" ]; then
	  find . -name Mp3RetagTest.java | xargs rm -f # fails when run with mop
  elif [ ${p_name} == "multi-thread-context" ]; then
    find . -name JavassistTest.kt | xargs rm -f
  elif [ ${p_name} == "OpenTripPlanner" ]; then
    find . -name TestIntermediatePlaces.java | xargs rm -f
    find . -name LinkingTest.java | xargs rm -f
    find . -name TestTransfers.java | xargs rm -f
    find . -name TestBanning.java | xargs rm -f
    find . -name TestFares.java | xargs rm -f
    find . -name GraphIndexTest.java | xargs rm -f
    find . -name PointSetTest.java | xargs rm -rf
    find . -name InitialStopsTest.java | xargs rm -f
    find . -name CSVPopulationTest.java | xargs rm -f
    find . -name EncodedPolylineJSONSerializerTest.java | xargs rm -f
    find . -name BanoGeocoderTest.java | xargs rm -f
  elif [ ${p_name} == "scribe-java" ]; then
    git checkout -f pom.xml
    sed -i "s|<release>\${java.release}</release>|<source>1.7</source><target>1.7</target>|g" pom.xml
  elif [ ${p_name} == "smartsheet-java-sdk" ]; then
    find . -name SheetResourcesImplTest.java | xargs rm -f
  elif [ "${p_name}" == "stream-lib" ]; then
	  find . -name TDigestTest.java | xargs rm -f
  elif [ ${p_name} == "underscore-java" ]; then
	  find . -name _Test.java | xargs rm -f
    find . -name LodashTest.java | xargs rm -f
  fi
}
