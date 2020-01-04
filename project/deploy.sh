#! /bin/sh

REPO="https://oss.sonatype.org/service/local/staging/deploy/maven2/"

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
  FILES="$BASE.jar $BASE-javadoc.jar:javadoc $BASE-sources.jar:sources"

  for FILE in $FILES; do
    JAR=`echo "$FILE" | cut -d ':' -f 1`
    CLASSIFIER=`echo "$FILE" | cut -d ':' -f 2`

    if [ ! "$CLASSIFIER" = "$JAR" ]; then
      ARG="-Dclassifier=$CLASSIFIER"
    else
      ARG=""
    fi

    expect << EOF
set timeout 300
spawn mvn gpg:sign-and-deploy-file -Dkeyname=$KEY -DpomFile=$POM -Dfile=$JAR $ARG -Durl=$REPO -DrepositoryId=sonatype-nexus-staging
expect "GPG Passphrase:"
send "$PASS\r"
expect "BUILD SUCCESS"
expect eof
EOF
  done
}

SCALA_MODULES="api:reactivemongo-bson-api specs2:reactivemongo-specs2 msb-compat:reactivemongo-bson-msb-compat geo:reactivemongo-bson-geo monocle:reactivemongo-bson-monocle"
SCALA_VERSIONS="2.10 2.11 2.12 2.13"
BASES=""

NOSHADED=`echo "$VERSION" | grep -- '-noshaded$' | wc -l`

for V in $SCALA_VERSIONS; do
    for M in $SCALA_MODULES; do
        B=`echo "$M" | cut -d ':' -f 1`

        if [ "$NOSHADED" -eq 0 ]; then
          SCALA_DIR="$B/target/shaded/scala-$V"
        else
          SCALA_DIR="$B/target/noshaded/scala-$V"
        fi

        if [ ! -d "$SCALA_DIR" ]; then
            echo "Skip Scala version $V for $M"
        else
            N=`echo "$M" | cut -d ':' -f 2`
            BASES="$BASES $SCALA_DIR/$N"_$V-$VERSION
        fi
    done
done

for B in $BASES; do
  deploy "$B"
done
