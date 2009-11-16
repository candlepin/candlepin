#!/usr/bin/python

import httplib, urllib
import sys
import simplejson

response = urllib.urlopen('http://localhost:8080/candlepin/product')
rsp = response.read()
print("list: %s" % rsp)
prod = simplejson.loads(rsp)
print(type(prod))
