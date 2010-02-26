#!/usr/bin/python

import httplib, urllib
import sys
import simplejson

response = urllib.urlopen('http://localhost:8080/candlepin/pool')
rsp = response.read()
pool = simplejson.loads(rsp)
for p in pool['pool']:
    print("Product %s with qty of %s valid from %s to %s" % (p['productId'], p['maxMembers'], p['startDate'], p['endDate']))

