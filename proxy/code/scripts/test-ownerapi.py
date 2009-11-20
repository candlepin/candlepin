#!/usr/bin/python

import httplib, urllib
import sys
import simplejson

response = urllib.urlopen('http://localhost:8080/candlepin/owner')
rsp = response.read()
print("get: %s" % rsp)

