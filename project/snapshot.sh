#! /bin/sh

export PUBLISH_REPO_NAME="Sonatype Nexus Repository Manager"
export PUBLISH_REPO_ID="oss.sonatype.org"
export PUBLISH_REPO_URL="https://oss.sonatype.org/content/repositories/snapshots"

if [ -z "$PUBLISH_USER" ]; then
  export PUBLISH_USER="$USER"
fi

echo "Password: "
read PASS
export PUBLISH_PASS="$PASS"

sbt +publish

REACTIVEMONGO_SHADED=false sbt +publish
