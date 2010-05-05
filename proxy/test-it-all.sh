#!/bin/bash

KILLPSQL=1
IMPORTDIR=/home/adrian/src/cp_product_utils/
UNITTEST="test=no"
#UNITTEST=

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


