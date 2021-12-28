#! /usr/bin/env bash

set -e

SCRIPT_DIR=`dirname $0 | sed -e "s|^\./|$PWD/|"`

cd "$SCRIPT_DIR/.."

sbt ++$SCALA_VERSION ';scalafixAll -check ;scalafmtAll'

git diff --exit-code || (
  echo "ERROR: Scalariform check failed, see differences above."
  echo "To fix, format your sources using ./build scalafmtAll before submitting a pull request."
  echo "Additionally, please squash your commits (eg, use git commit --amend) if you're going to update this pull request."
  false
)

source "$SCRIPT_DIR/jvmopts.sh"

export JVM_OPTS
export SBT_OPTS

TEST_ARGS=";error ;test:compile ;mimaReportBinaryIssues ;warn"

if [ ! `echo "n$SCALA_VERSION" | sed -e 's/2.13.*/o/'` = "no" ]; then
  TEST_ARGS="$TEST_ARGS ;msbCompat/testOnly"
fi

TEST_ARGS="$TEST_ARGS ;doc"

cat > /dev/stdout <<EOF
- JVM options: $JVM_OPTS
- SBT options: $SBT_OPTS
- Test arguments: $TEST_ARGS
EOF

sbt ++$SCALA_VERSION "$TEST_ARGS"
