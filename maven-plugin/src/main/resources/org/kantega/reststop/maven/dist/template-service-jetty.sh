#!/bin/bash
#
# %1$s      Shell script for starting and stopping %1$s
#
# chkconfig: - 95 5
#
### BEGIN INIT INFO
# Provides: %1$s
# Required-Start: $network $syslog
# Required-Stop: $network $syslog
# Default-Start:
# Default-Stop:
# Description: Starts and stops %1$s
# Short-Description: Starts and stops %1$s
### END INIT INFO

NAME=%1$s

# Source the settings
if [ -r "/etc/default/$NAME" ]; then
    source "/etc/default/$NAME"
fi
if [ -r "$CNF" ]; then
    source $CNF
fi

LOGBASE=RESTSTOPLOGBASE
INSTDIR=RESTSTOPINSTDIR
MAX_WAIT_KILL=60
PIDFILE="/var/run/$NAME.pid"

LOG=${LOGBASE:-/var/log}/$NAME.log
DIR=${INSTDIR:-/opt]/$NAME



isRunning()
{
    if [ -f $PIDFILE ]; then
        PID=`cat $PIDFILE`
        if ps -p $PID > /dev/null; then
            return 1
        else
            return 0
        fi
        rm $PIDFILE
    else
        return 1
    fi
}


# Function for starting the service
startConsole()
{
    SAVEPWD=$PWD
    cd $DIR
    STARTCMD="/opt/$NAME/jetty/bin/jetty.sh start"
    $STARTCMD >> $LOG 2>&1 & echo \$! > $PIDFILE

    cd $SAVEPWD
}

# Function for stopping the service
stopConsole()
{

    if [ -f $PIDFILE ]; then
        PID=`cat $PIDFILE`
        if ps -p $PID > /dev/null; then
            kill $PID
            i=0;
            while [ -d /proc/$PID ] ; do
                echo -n "."
                sleep 1
                let "i = $i + 1"
                if [ $i -ge $MAX_WAIT_KILL ] ; then
                   echo
                   echo -n "Force-killing PID $PID"
                   kill -9 $PID
                   break
                fi
            done

        else
            echo "No process to kill, removing PID."
        fi
        rm $PIDFILE
    fi
}

case "$1" in
    start)
    if [[ -f "${PIDFILE}" ]] ; then
        PID=`cat $PIDFILE`
        if ps -p $PID > /dev/null; then
            echo "$NAME is already started."
        else
            # The process does not exist, we need to remove the pid file:
            echo "PID file is still present but the process is not running, removing $PIDFILE."
            rm -f "${PIDFILE}"
            echo -n "Starting $NAME"
            startConsole
            echo "."
        fi
    else
        echo -n "Starting $NAME"
        startConsole
        echo "."
    fi
    ;;
    stop)
    if [ -f $PIDFILE ]; then
        echo -n "Stopping $NAME"
        stopConsole
        echo "$NAME is stopped"
    else
        echo "$NAME is already stopped."
    fi
    ;;
    restart|force-reload)
    echo -n "Restarting $NAME"
    stopConsole
    sleep 3
    startConsole
    echo "."
    ;;
    log)
    less +G $LOG
    ;;
    dumpstack)
    if [[ -f "${PIDFILE}" ]] ; then
        PID=`cat $PIDFILE`
        if ps -p $PID > /dev/null; then
           kill -3 $PID
           less +G $LOG
        else
            echo Process $PID is not running.
        fi
    else
      echo $NAME is not running.
    fi
    ;;
    lsof)
    if [[ -f "${PIDFILE}" ]] ; then
        PID=`cat $PIDFILE`
        if ps -p $PID > /dev/null; then
           lsof -p $PID |less
        else
            echo Process $PID is not running.
        fi
    else
      echo $NAME is not running.
    fi
    ;;
    status)
    if [[ -f "${PIDFILE}" ]] ; then
        PID=`cat $PIDFILE`
        if ps -p $PID > /dev/null; then
           echo "$NAME is running as PID $PID"
        else
            echo "$NAME is not running (stale PID is $PID)"
        fi
    else
      echo "$NAME is stopped"
    fi
    ;;
    *)
    echo "Usage: $NAME {start|stop|restart|status|force-reload|redeploy|log|dumpstack|lsof|update-service}" >&2
    exit 1
    ;;
esac

exit 0
# -- eof --