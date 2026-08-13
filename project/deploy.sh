#! /bin/sh

# curl -D - -X POST -u "$SONATYPE_USERNAME:$SONATYPE_PASSWORD" "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/org.reactivemongo"

REPO="https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"

if [ $# -lt 2 ]; then
    echo "Usage $0 version gpg-key"
    exit 1
fi

VERSION="$1"
KEY="$2"

echo "Password: "
read -s PASS

function deploy {
  BASE="$1"
  POM="$BASE.pom"

  expect << EOF
set timeout 300
log_user 0
spawn mvn gpg:sign-and-deploy-file \
  -Dkeyname=$KEY \
  -Dpassphrase=$PASS \
  -DpomFile=$POM \
  -Dfile=$BASE.jar \
  -Djavadoc=$BASE-javadoc.jar \
  -Dsources=$BASE-sources.jar \
  -Durl=$REPO \
  -DrepositoryId=sonatype-nexus-staging
log_user 1
expect "BUILD SUCCESS"
expect eof
EOF
}

SCALA_MODULES="reactivemongo-bson-api reactivemongo-bson-specs2 reactivemongo-bson-msb-compat reactivemongo-bson-geo reactivemongo-bson-monocle reactivemongo-bson-builder"
SCALA_VERSIONS="2.11 2.12 2.13 3.3.8"
BASES=""

QUALIFIER=""
WO_QUALIFIER="$VERSION"

if [ `expr index "$VERSION" '-'` -gt 0 ]; then
  QUALIFIER="${VERSION#*-}"
  WO_QUALIFIER="${VERSION%%-*}"
fi

for V in $SCALA_VERSIONS; do
  MV="${V/#3*/3}"

  for M in $SCALA_MODULES; do
    SD=(target/out/jvm/scala-${V}*/$M/shaded)
    SDS="$SD"
    SD=(target/out/jvm/scala-${V}*/$M/noshaded)
    SDS="$SDS $SD"

    B=""

    for SCALA_DIR in $SDS; do
      if [ ! -d "$SCALA_DIR" ]; then
        echo "Skip Scala version $V for $M"
      else
        if [ `echo "$SCALA_DIR" | grep noshaded | wc -l` -ne 0 ]; then
          if [ ! -z $QUALIFIER ]; then
            B="$SCALA_DIR/$M"_"$MV-$WO_QUALIFIER-noshaded.${QUALIFIER}"
          else
            B="$SCALA_DIR/$M"_"$MV-${VERSION}-noshaded"
          fi
        else
          B="$SCALA_DIR/$M"_$MV-$VERSION
        fi
      fi

      BASES="$BASES $B"
    done
  done
done

for B in $BASES; do
  deploy "$B"
done
