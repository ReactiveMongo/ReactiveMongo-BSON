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

# OpenSSL
if [ ! -L "$HOME/ssl/lib/libssl.so.1.0.0" ] && [ ! -f "$HOME/ssl/lib/libssl.so.1.0.0" ]; then
  echo "[INFO] Building OpenSSL"

  cd /tmp
  curl -s -o - https://www.openssl.org/source/openssl-1.0.1s.tar.gz | tar -xzf -
  cd openssl-1.0.1s
  rm -rf "$HOME/ssl" && mkdir "$HOME/ssl"
  ./config -shared enable-ssl2 --prefix="$HOME/ssl" > /dev/null
  make depend > /dev/null
  make install > /dev/null

  ln -s "$HOME/ssl/lib/libssl.so.1.0.0" "$HOME/ssl/lib/libssl.so.10"
  ln -s "$HOME/ssl/lib/libcrypto.so.1.0.0" "$HOME/ssl/lib/libcrypto.so.10"
fi

export LD_LIBRARY_PATH="$HOME/ssl/lib:$LD_LIBRARY_PATH"

# Build MongoDB
MONGO_MINOR="4.0.0"

if [ "$MONGO_VER" = "2_6" ]; then
  MONGO_MINOR="2.6.12"
fi

# Build MongoDB
echo "[INFO] Building MongoDB ${MONGO_MINOR} ..."

cd "$HOME"

MONGO_ARCH="x86_64-amazon"

MONGO_ARCH="x86_64-amazon"

if [ "$MONGO_VER" = "2_6" ]; then
  MONGO_ARCH="x86_64"
fi

MONGO_HOME="$HOME/mongodb-linux-$MONGO_ARCH-$MONGO_MINOR"

if [ ! -x "$MONGO_HOME/bin/mongod" ]; then
    if [ -d "$MONGO_HOME" ]; then
      rm -rf "$MONGO_HOME"
    fi

    curl -s -o - "https://fastdl.mongodb.org/linux/mongodb-linux-$MONGO_ARCH-$MONGO_MINOR.tgz" | tar -xzf -
    chmod u+x "$MONGO_HOME/bin/mongod"
fi

echo "[INFO] MongoDB available at $MONGO_HOME"

PATH="$MONGO_HOME/bin:$PATH"

mkdir /tmp/mongodb

# MongoDB setup
MAX_CON=`ulimit -n`

if [ $MAX_CON -gt 1024 ]; then
    MAX_CON=`expr $MAX_CON - 1024`
fi

echo "[INFO] Max connection: $MAX_CON"

if [ "$MONGO_VER" = "2_6" ]; then
  cp "$SCRIPT_DIR/mongod26.conf" /tmp/mongod.conf
else
  cp "$SCRIPT_DIR/mongod3.conf" /tmp/mongod.conf
fi

echo "  maxIncomingConnections: $MAX_CON" >> /tmp/mongod.conf

echo "# MongoDB Configuration:"
cat /tmp/mongod.conf

MONGOD_CMD="mongod -f /tmp/mongod.conf --fork"

if [ `which numactl | wc -l` -gt 0 ]; then
    numactl --interleave=all $MONGOD_CMD
else
    $MONGOD_CMD
fi

MONGOD_PID=`ps -o pid,comm -u $USER | grep 'mongod$' | awk '{ printf("%s\n", $1); }'`

if [ "x$MONGOD_PID" = "x" ]; then
    echo -e "\n[ERROR] Fails to start the custom 'mongod' instance" > /dev/stderr

    mongod --version
    PID=`ps -o pid,comm -u $USER | grep 'mongod$' | awk '{ printf("%s\n", $1); }'`

    if [ ! "x$PID" = "x" ]; then
        pid -p $PID
    else
        echo "[ERROR] MongoDB process not found" > /dev/stderr
    fi

    tail -n 100 /tmp/mongod.log

    exit 1
fi

# Export environment for integration tests

cat > /tmp/integration-env.sh <<EOF
PATH="$PATH"
LD_LIBRARY_PATH="$LD_LIBRARY_PATH"
EOF
