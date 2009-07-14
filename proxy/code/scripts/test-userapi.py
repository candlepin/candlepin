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
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
conn.close()
print("create: %s" % rsp)

response = urllib.urlopen('http://localhost:8080/candlepin/user/candlepin')
rsp = response.read()
print("get: %s" % rsp)

response = urllib.urlopen('http://localhost:8080/candlepin/user/list')
rsp = response.read()
print("list: %s" % rsp)

response = urllib.urlopen('http://localhost:8080/candlepin/user/listusers')
rsp = response.read()
print("listusers: %s" % rsp)

response = urllib.urlopen('http://localhost:8080/candlepin/user/uselist')
rsp = response.read()
print("uselist: %s" % rsp)

response = urllib.urlopen('http://localhost:8080/candlepin/user/listobjects')
rsp = response.read()
print("listobjects: %s" % rsp)

response = urllib.urlopen('http://localhost:8080/candlepin/user/listbasemodel')
rsp = response.read()
print("listbasemodel: %s" % rsp)
