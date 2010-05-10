#!/bin/bash

KILLPSQL=1
IMPORTDIR=/home/adrian/src/cp_product_utils/
#IMPORTDIR=
UNITTEST="test=no"
#UNITTEST="test"

if [ "$IMPORTDIR" == "" ]; then
	echo "IMPORTDIR needs to be set to dir containing cp_product_utils"
	exit
fi

# clean up
buildr clean

#build and test
buildr "$UNITTEST"

# remove any old cruft lying around
sudo rm -rf /etc/pki/consumers/* /etc/pki/entitlements/*


if [ "$KILLPSQL" != "" ]; then
	killall psql
fi
 
# deploy, populate db, etc
GENDB=1 IMPORTDIR=$IMPORTDIR buildconf/scripts/deploy

# run cucumber functional tests
buildr cucumber


