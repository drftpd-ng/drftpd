#!/bin/bash
killall rmiregistry
export CLASSPATH=classes
exec rmiregistry
