#!/bin/bash

bin=`dirname "$0"`
bin=`cd "$bin"; pwd`

. "$bin"/memngt-config.sh

HOSTLIST="${MEMNGT_CONF_DIR}/slaves"

if [ ! -f $HOSTLIST ]; then
	echo $HOSTLIST is not a valid slave list
	exit 1
fi


# cluster mode, only bring up job manager locally and a task manager on every slave host
# $NEPHELE_BIN_DIR/nephele-jobmanager.sh stop

while read line
do
	HOST=$line
	ssh -n $HOST -- "nohup /bin/bash $MEMNGT_BIN_DIR/memngt-daemon.sh stop &" &
done < $HOSTLIST
wait
