#! /bin/sh

export PUBLISH_REPO_NAME="Sonatype Nexus Repository Manager"
export PUBLISH_REPO_ID="central.sonatype.com"
export PUBLISH_REPO_URL="https://central.sonatype.com/repository/maven-snapshots/"

if [ -z "$PUBLISH_USER" ]; then
  echo "User: "
  read PUBLISH_USER
fi

export PUBLISH_USER

echo "Password: "
read PASS
export PUBLISH_PASS="$PASS"

sbt +publish

REACTIVEMONGO_SHADED=false sbt +publish
