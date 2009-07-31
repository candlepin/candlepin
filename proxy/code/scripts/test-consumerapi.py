#!/usr/bin/python

import httplib, urllib
import sys

# GET list of consumers
response = urllib.urlopen('http://localhost:8080/candlepin/consumer/')
rsp = response.read()
print("list of users: %s" % rsp)

# GET candlepin user
response = urllib.urlopen('http://localhost:8080/candlepin/consumer/candlepin')
rsp = response.read()
print("get: %s" % rsp)

