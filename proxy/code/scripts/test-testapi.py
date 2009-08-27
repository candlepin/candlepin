#!/usr/bin/python

import httplib, urllib
import sys
import simplejson as json

if len(sys.argv) < 1:
    print("please supply a message")
    sys.exit(1)

print("------------ TESTING json create")
params = {"name":"now","uuid":"thiswork","stringList":["string1", "string2"]}
print(type(params['stringList']))
headers = {"Content-type":"application/json",
           "Accept": "application/json"}
conn = httplib.HTTPConnection("localhost", 8080)
print("creating object with %s" % params)
conn.request("POST", '/candlepin/test/', json.dumps(params), headers)
response = conn.getresponse()
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
conn.close()
print("------------ TESTING json get")
response = urllib.urlopen("http://localhost:8080/candlepin/test/")
rsp = response.read()
print("testjsonobject get: %s" % rsp)
