#!/usr/bin/python

import httplib, urllib
import sys

if len(sys.argv) < 1:
    print("please supply a message")
    sys.exit(1)

param = sys.argv[1]

params = urllib.urlencode({'message':sys.argv[1]})
headers = {"Content-type":"application/x-www-form-urlencoded",
           "Accept": "application/json"}
conn = httplib.HTTPConnection("localhost", 8080)
conn.request("POST", '/candlepin/helloworld', params, headers)
#response = conn.getresponse()
#print response.status, response.reason
#rsp = response.read()
conn.close()
#print rsp

# test creating org
params = urllib.urlencode({'name':'test-org-client-created-' + param})
conn.request("POST", '/candlepin/org', params, headers)
response = conn.getresponse()
print response.status, response.reason
rsp = response.read()
print(rsp)

# test creating consumer
params = urllib.urlencode({'name':'test-consumer-client-created-' + param})
conn.request("POST", '/candlepin/consumer', params, headers)
response = conn.getresponse()
print response.status, response.reason
rsp = response.read()
print(rsp)

response = urllib.urlopen('http://localhost:8080/candlepin/consumer/list')
rsp = response.read()
print(rsp)

