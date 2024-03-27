#!/bin/sh
set -eu
export GITHUB="true"
sh -c "java -jar /app.jar $*"