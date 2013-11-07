#!/bin/sh
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
#
jettycmd=INSTALLDIR/APPNAME/jetty/bin/jetty.sh
case $1 in
    start|stop|restart)
    $jettycmd $*
    ;;
    *)
    exit -1
    ;;
esac

exit 0
#