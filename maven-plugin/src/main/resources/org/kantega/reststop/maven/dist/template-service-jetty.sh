#!/bin/sh
jettycmd=/INSTALLDIR/APPNAME/jetty/bin/jetty.sh
case $1 in
    start|stop|restart)
    $jettycmd $*
    ;;
    *)
    exit -1
    ;;
esac