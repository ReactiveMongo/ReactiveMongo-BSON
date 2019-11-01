#! /bin/bash

# Clean cache
rm -rf "$HOME/.ivy2/local/org.reactivemongo"

# Prepare integration env

SCRIPT_DIR=`dirname $0 | sed -e "s|^\./|$PWD/|"`

if [ "x$MONGO_VER" = "x" ]; then
  echo "MONGO_VER is not defined"
  exit 2
fi

cat > /dev/stdout <<EOF
MongoDB major version: $MONGO_VER
EOF

# Build MongoDB
MONGO_MINOR="4.0.0"

if [ "$MONGO_VER" = "2_6" ]; then
  MONGO_MINOR="2.6.12"
fi

cd "$HOME"
