#!/bin/bash
# 
# Clean up experiment data. If argument is provided, do cleanup for only one project.
# Please be careful with this!

PROJECT_NAME=${1:-}

SCRIPTS_DIR=$(cd $(dirname "$0") && pwd)

echo -n "Do you confirm to proceed deletion? Reply 'y' to proceed, otherwise abort: "
read reply
if [ "${reply}" != "y" ]; then
  exit 1
fi
if [ -z "$PROJECT_NAME" ]; then
  rm -rf ${SCRIPTS_DIR}/../env-*
  rm -rf ../logs ../data/generated-data/* ../results ../experiment-projects
else
  rm -rf "$SCRIPTS_DIR/../logs/$PROJECT_NAME.txt"
  rm -rf "$SCRIPTS_DIR/../env-$PROJECT_NAME"
  rm -rf "$SCRIPTS_DIR/../experiment-projects/$PROJECT_NAME"
  rm -rf "$SCRIPTS_DIR/../data/generated-data/$PROJECT_NAME"
fi
rm -rf ${SCRIPTS_DIR}/apache*
