#!/bin/bash

STARTSTOP=$1

bin=`dirname "$0"`
bin=`cd "$bin"; pwd`

. "$bin"/memngt-config.sh

if [ "$MEMNGT_PID_DIR" = "" ]; then
	MEMNGT_PID_DIR=/tmp
fi

if [ "$MEMNGT_IDENT_STRING" = "" ]; then
	MEMNGT_IDENT_STRING="$USER"
fi

out=$MEMNGT_LOG_DIR/memngt-$MEMNGT_IDENT_STRING-daemon-$HOSTNAME.out
pid=$MEMNGT_PID_DIR/memngt-$MEMNGT_IDENT_STRING-daemon.pid

JVM_ARGS="$JVM_ARGS -Xms64m -Xmx64m"

case $STARTSTOP in

	(start)
		mkdir -p "$MEMNGT_PID_DIR"
		if [ -f $pid ]; then
			if kill -0 `cat $pid` > /dev/null 2>&1; then
				echo Memory negotiator daemon running as process `cat $pid` on host $HOSTNAME.  Stop it first.
				exit 1
     			fi
		fi

		echo Starting memory negotiator daemon on host $HOSTNAME
		$JAVA_HOME/bin/java $JVM_ARGS -classpath $CLASSPATH edu.berkeley.icsi.memngt.daemon.Daemon > "$out" 2>&1 < /dev/null &
		echo $! > $pid
	;;

	(stop)
		if [ -f $pid ]; then
			if kill -0 `cat $pid` > /dev/null 2>&1; then
				echo Stopping memory negotiator daemon on host $HOSTNAME
				kill `cat $pid`
			else
				echo No memory negotiator daemon to stop on host $HOSTNAME
			fi
		else
			echo No memory negotiator daemon to stop on host $HOSTNAME
		fi
	;;

	(*)
		echo Please specify start or stop
	;;

esac
