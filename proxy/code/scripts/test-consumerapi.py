#!/usr/bin/python

import httplib, urllib
import sys
import simplejson as json

# POST new user
print("create consumer")
#info = {"parent":"","type":"system","metadata":{"arch":"i386","cpu":"intel"}}
info = {"parent":"","type":"system", "metadata":{"entry":[{"key":"arch","value":"i386"},{"key":"cpu","value":"Intel"}]}}
params = {"owneruuid":"","info":info}
headers = {"Content-type":"application/json",
           "Accept": "application/json"}
conn = httplib.HTTPConnection("localhost", 8080)
conn.request("POST", '/candlepin/consumer/', json.dumps(params), headers)
response = conn.getresponse()
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
print("created consumer: %s" % rsp)
conn.close()

# GET list of consumers
response = urllib.urlopen('http://localhost:8080/candlepin/consumer/')
rsp = response.read()
print("list of consumers: %s" % rsp)

# GET candlepin user
response = urllib.urlopen('http://localhost:8080/candlepin/consumer/candlepin')
rsp = response.read()
print("get: %s" % rsp)

# GET candlepin user
response = urllib.urlopen('http://localhost:8080/candlepin/consumer/info')
rsp = response.read()
print("get info: %s" % rsp)

{
}
