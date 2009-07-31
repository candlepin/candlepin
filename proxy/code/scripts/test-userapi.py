#!/usr/bin/python

import httplib, urllib
import sys
import simplejson as json

# POST new user
params = {"login":"testapi","password":"sw3etnothings"}
headers = {"Content-type":"application/json",
           "Accept": "application/json"}
conn = httplib.HTTPConnection("localhost", 8080)
conn.request("POST", '/candlepin/user/', json.dumps(params), headers)
response = conn.getresponse()
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
conn.close()

# GET testapi user
response = urllib.urlopen('http://localhost:8080/candlepin/user/testapi')
rsp = response.read()
print("get: %s" % rsp)

# GET list of users
response = urllib.urlopen('http://localhost:8080/candlepin/user/')
rsp = response.read()
print("list of users: %s" % rsp)
