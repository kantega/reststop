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

NAME=RESTSTOPNAME
export JETTY_USER=RESTSTOPNAME

# Source the settings
if [ -r "/etc/default/$NAME" ]; then
    source "/etc/default/$NAME"
fi
if [ -r "$CNF" ]; then
    source $CNF
fi

INSTDIR=RESTSTOPINSTDIR
APPDIR="$INSTDIR/$NAME"

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
      bin/jetty.sh restart
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
