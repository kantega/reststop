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
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Description: Starts and stops %1$s
# Short-Description: Starts and stops %1$s
### END INIT INFO

NAME=@RESTSTOPNAME
export JETTY_USER=@RESTSTOPNAME

INSTDIR=@RESTSTOPINSTDIR
APPDIR="$INSTDIR/$NAME"


# Source the settings
if [ -r "$APPDIR/jetty/defaults/$NAME" ]; then
    source "$APPDIR/jetty/defaults/$NAME"
fi
if [ -r "/etc/default/$NAME" ]; then
    source "/etc/default/$NAME"
fi
if [ -r "$CNF" ]; then
    source $CNF
fi


case "$1" in
    start)
      SAVEPWD=$PWD
      cd $APPDIR/jetty/
      mkdir -p /var/log/$NAME
      chown $NAME:$NAME /var/log/$NAME
      bin/jetty.sh start
      cd $SAVEPWD
    ;;
    stop)
      SAVEPWD=$PWD
      cd $APPDIR/jetty/
      bin/jetty.sh stop
      cd $SAVEPWD
    ;;
    restart)
      SAVEPWD=$PWD
      cd $APPDIR/jetty/
      bin/jetty.sh stop
      bin/jetty.sh start
      cd $SAVEPWD
    ;;
    check)
      SAVEPWD=$PWD
      cd $APPDIR/jetty/
      bin/jetty.sh check
      cd $SAVEPWD
    ;;
    *)
        echo "Usage: $NAME {start|stop|restart|check}" >&2
        exit 1
    ;;
esac

exit 0
#
