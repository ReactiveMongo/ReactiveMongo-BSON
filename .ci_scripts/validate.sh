#! /usr/bin/env bash

set -e

SCRIPT_DIR=`dirname $0 | sed -e "s|^\./|$PWD/|"`

cd "$SCRIPT_DIR/.."

SBT_TASK="scalafmtCheckAll"

SV="v${SCALA_VERSION/2.11.*/2.11}"

if [ "$SV" != "v2.11" ]; then
    SBT_TASK="$SBT_TASK ;scalafixAll -check"

    source "$SCRIPT_DIR/jvmopts.sh"
    
    export JVM_OPTS
    export SBT_OPTS
fi

sbt ++$SCALA_VERSION "$SBT_TASK" || (
  echo "ERROR: SBT preliminary checks failed while running: $SBT_TASK"
  echo "Please inspect the errors above to identify whether this is a formatting, scalafix, or dependency issue."
  echo "If this is formatting-related, run ./build scalafmtAll before submitting a pull request."
  echo "Additionally, please squash your commits (eg, use git commit --amend) if you're going to update this pull request."
  false
)

if [ "$SV" = "v2.11" ]; then
    source "$SCRIPT_DIR/jvmopts.sh"
    
    export JVM_OPTS
    export SBT_OPTS
fi

TEST_ARGS=";error ;test:compile ;mimaReportBinaryIssues ;warn ;testOnly ;doc"

cat > /dev/stdout <<EOF
- JVM options: $JVM_OPTS
- SBT options: $SBT_OPTS
- Test arguments: $TEST_ARGS
EOF

sbt ++$SCALA_VERSION "$TEST_ARGS"
