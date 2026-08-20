#!/bin/bash
cd "$(dirname "$0")"

if [ -d /opt/homebrew/opt/openjdk@21 ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21
    export PATH="$JAVA_HOME/bin:$PATH"
fi

JAR="target/PotOfGreedManager-1.0-SNAPSHOT.jar"
if [ ! -f "$JAR" ]; then
    echo "$JAR not found - building it first (this can take a minute)..."
    if [ -d /opt/homebrew/opt/maven ]; then
        export PATH="/opt/homebrew/opt/maven/bin:$PATH"
    fi
    mvn -Dmaven.test.skip=true package
fi

java -jar "$JAR"
