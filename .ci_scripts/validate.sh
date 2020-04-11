#! /usr/bin/env bash

set -e

SCRIPT_DIR=`dirname $0 | sed -e "s|^\./|$PWD/|"`

cd "$SCRIPT_DIR/.."

sbt ++$SCALA_VERSION scalariformFormat test:scalariformFormat
git diff --exit-code || (
  echo "ERROR: Scalariform check failed, see differences above."
  echo "To fix, format your sources using ./build scalariformFormat test:scalariformFormat before submitting a pull request."
  echo "Additionally, please squash your commits (eg, use git commit --amend) if you're going to update this pull request."
  false
)

source "$SCRIPT_DIR/jvmopts.sh"

export JVM_OPTS
export SBT_OPTS

TEST_ARGS=";mimaReportBinaryIssues"

if [ ! `echo "n$SCALA_VERSION" | sed -e 's/2.13.*/o/'` = "no" ]; then
  TEST_ARGS=";scapegoat $TEST_ARGS"
fi

if [ "v$MONGO_VER" = "v2_6" ]; then
  TEST_ARGS="$TEST_ARGS ;testOnly -- exclude gt_mongo32"
else
  TEST_ARGS="$TEST_ARGS ;testOnly -- exclude mongo2"
fi

if [ ! "v$SCALA_VERSION" = "v2.13.1" ]; then
  TEST_ARGS="$TEST_ARGS ;msbCompat/testOnly"
fi

TEST_ARGS="$TEST_ARGS ;doc"

cat > /dev/stdout <<EOF
- JVM options: $JVM_OPTS
- SBT options: $SBT_OPTS
- Test arguments: $TEST_ARGS
EOF

sbt ++$SCALA_VERSION "$TEST_ARGS"
