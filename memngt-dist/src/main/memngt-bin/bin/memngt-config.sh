#!/bin/bash

# Resolve links
this="$0"
while [ -h "$this" ]; do
  ls=`ls -ld "$this"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '.*/.*' > /dev/null; then
    this="$link"
  else
    this=`dirname "$this"`/"$link"
  fi
done

# Convert relative path to absolute path
bin=`dirname "$this"`
script=`basename "$this"`
bin=`cd "$bin"; pwd`
this="$bin/$script"

# Define JAVA_HOME if it is not already set
if [ -z "${JAVA_HOME+x}" ]; then
        JAVA_HOME=/usr/lib/jvm/java-6-sun/
fi

# Define HOSTNAME if it is not already set
if [ -z "${HOSTNAME+x}" ]; then
        HOSTNAME=`hostname`
fi

# Define the main directory of the memngt installation
MEMNGT_ROOT_DIR=`dirname "$this"`/..
MEMNGT_CONF_DIR=$MEMNGT_ROOT_DIR/conf
MEMNGT_BIN_DIR=$MEMNGT_ROOT_DIR/bin
MEMNGT_LIB_DIR=$MEMNGT_ROOT_DIR/lib
MEMNGT_LOG_DIR=$MEMNGT_ROOT_DIR/log

# Arguments for the JVM. 
JVM_ARGS="-Djava.net.preferIPv4Stack=true"

# Default classpath 
CLASSPATH=$( echo $MEMNGT_LIB_DIR/*.jar . | sed 's/ /:/g' )
