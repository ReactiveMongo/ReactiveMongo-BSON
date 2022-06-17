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
log_user 0
spawn mvn gpg:sign-and-deploy-file -Dkeyname=$KEY -Dpassphrase=$PASS -DpomFile=$POM -Dfile=$JAR $ARG -Durl=$REPO -DrepositoryId=sonatype-nexus-staging
log_user 1
expect "BUILD SUCCESS"
expect eof
EOF
  done
}

SCALA_MODULES="api:reactivemongo-bson-api specs2:reactivemongo-bson-specs2 msb-compat:reactivemongo-bson-msb-compat geo:reactivemongo-bson-geo monocle:reactivemongo-bson-monocle"
SCALA_VERSIONS="2.10 2.11 2.12 2.13 3.1.3-RC5"
BASES=""

QUALIFIER=""
WO_QUALIFIER="$VERSION"

if [ `expr index "$VERSION" '-'` -gt 0 ]; then
  QUALIFIER=`echo "$VERSION" | cut -d '-' -f 2`
  WO_QUALIFIER=`echo "$VERSION" | cut -d '-' -f 1`
fi

for V in $SCALA_VERSIONS; do
  MV=`echo "$V" | sed -e 's/^3.*/3/'`

  for M in $SCALA_MODULES; do
    B=`echo "$M" | cut -d ':' -f 1`
    SDS="$B/target/shaded/scala-$V $B/target/noshaded/scala-$V"

    for SCALA_DIR in $SDS; do
      if [ ! -d "$SCALA_DIR" ]; then
        echo "Skip Scala version $V for $M"
      else
        N=`echo "$M" | cut -d ':' -f 2`

        if [ `echo "$SCALA_DIR" | grep noshaded | wc -l` -ne 0 ]; then
          if [ ! -z $QUALIFIER ]; then
            BASES="$BASES $SCALA_DIR/$N"_"$MV-$WO_QUALIFIER-noshaded-"`echo "$VERSION" | cut -d '-' -f 2`
          else
            BASES="$BASES $SCALA_DIR/$N"_"$MV-$VERSION-noshaded"
          fi
        else
          BASES="$BASES $SCALA_DIR/$N"_$MV-$VERSION
        fi
      fi
    done
  done
done

for B in $BASES; do
  deploy "$B"
done
