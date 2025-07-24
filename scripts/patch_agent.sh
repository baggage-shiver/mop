#!/bin/bash
#
# Modify AspectJ to redirect weaving message
# Usage: export AJC_LOG=/path/to/log
AGENT_JAR=$1

if [[ ! -f ${AGENT_JAR} ]]; then
	echo "Missing ${AGENT_JAR}"
	exit 1
fi

# Compile our modification
javac MessageWriter.java

rm -rf ./org/aspectj/bridge/

mkdir -p ./org/aspectj/bridge/

mv MessageWriter.class ./org/aspectj/bridge/

# Replace the compiled code
zip $AGENT_JAR ./org/aspectj/bridge/MessageWriter.class

rm -rf ./org/aspectj/bridge/
