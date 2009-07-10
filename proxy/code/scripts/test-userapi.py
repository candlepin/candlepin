#!/usr/bin/python

import httplib, urllib
import sys

if len(sys.argv) < 1:
    print("please supply a message")
    sys.exit(1)

params = urllib.urlencode({'login':'candlepin', 'password':'cp_p@s$w0rd'})
headers = {"Content-type":"application/json",
           "Accept": "application/json"}
conn = httplib.HTTPConnection("localhost", 8080)
conn.request("POST", '/candlepin/user', params, headers)
response = conn.getresponse()
print response.status, response.reason
rsp = response.read()
conn.close()
print rsp

response = urllib.urlopen('http://localhost:8080/candlepin/user/candlepin')
rsp = response.read()
print(rsp)
