JVM_MAX_MEM="1G"

JAVA_MAJOR=`java -version 2>&1 | head -n 1 | cut -d '"' -f 2 | sed -e 's/\.[0-9].*//' | sed -e 's/_.*$//'`

if [ "v$JAVA_MAJOR" = "v10.0" ]; then
  JVM_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70"
else
  JVM_MAX_MEM="1760M" #1632M"
  JVM_OPTS="-Xmx$JVM_MAX_MEM -XX:ReservedCodeCacheSize=192m"
fi

# Disable sbt server for JDK 26+ to work around Unix domain socket JNI crash
# Check both exact match and numeric comparison for safety
if [ "$JAVA_MAJOR" = "26" ] || [ "$JAVA_MAJOR" = "27" ] || ([ "$JAVA_MAJOR" -gt "27" ] 2>/dev/null); then
  JVM_OPTS="$JVM_OPTS -Dsbt.server=false"
fi

#JVM_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap"

export _JAVA_OPTIONS="$JVM_OPTS"
