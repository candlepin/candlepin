#!/usr/bin/python

import httplib, urllib
import sys

# GET candlepin user
response = urllib.urlopen('http://localhost:8080/candlepin/user/candlepin')
rsp = response.read()
print("get: %s" % rsp)

# GET list of users
response = urllib.urlopen('http://localhost:8080/candlepin/user/')
rsp = response.read()
print("list of users: %s" % rsp)
