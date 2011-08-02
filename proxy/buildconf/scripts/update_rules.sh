#!/bin/bash

SERVERURL=https://localhost:8443/candlepin/rules
SRCDIR=~/src/candlepin/proxy

base64 $SRCDIR//src/main/resources/rules/default-rules.js > $SRCDIR/buildconf/rules.base64
curl  -H "Accept:text/plain" -H "Content-type: text/plain"  -k -u admin:admin -X POST $SERVERURL --data @$SRCDIR/buildconf/rules.base64
