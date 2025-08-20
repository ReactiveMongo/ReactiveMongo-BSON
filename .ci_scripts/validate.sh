#! /usr/bin/env bash

set -e

SCRIPT_DIR=`dirname $0 | sed -e "s|^\./|$PWD/|"`

cd "$SCRIPT_DIR/.."

SBT_TASK="scalafmtCheckAll"

if [ "v${SCALA_VERSION}" != "v2.11.12" ]; then
    SBT_TASK="$SBT_TASK ;scalafixAll -check"
fi

sbt ++$SCALA_VERSION "$SBT_TASK" || (
  echo "ERROR: Scalafmt check failed, see differences above."
  echo "To fix, format your sources using ./build scalafmtAll before submitting a pull request."
  echo "Additionally, please squash your commits (eg, use git commit --amend) if you're going to update this pull request."
  false
)

source "$SCRIPT_DIR/jvmopts.sh"

export JVM_OPTS
export SBT_OPTS

TEST_ARGS=";error ;test:compile ;mimaReportBinaryIssues ;warn ;testOnly ;doc"

cat > /dev/stdout <<EOF
- JVM options: $JVM_OPTS
- SBT options: $SBT_OPTS
- Test arguments: $TEST_ARGS
EOF

sbt ++$SCALA_VERSION "$TEST_ARGS"
