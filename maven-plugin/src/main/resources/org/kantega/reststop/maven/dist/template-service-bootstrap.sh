#!/bin/bash

$NAME="@NAME"

if [ -z "$JAVA" ]
then
  JAVA=$(which java)
fi

if [ -z "$JAVA" ]
then
  echo "Cannot find a Java JDK. Please set either set JAVA or put java (>=1.5) in your PATH." >&2
  exit 1
fi
BINDIR=$(dirname $0)
RESTSTOP_HOME=$(dirname $BINDIR)

cd $RESTSTOP_HOME

$JAVA $JAVA_OPTS -jar start.jar $RESTSTOP_HOME/conf/$NAME.conf