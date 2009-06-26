#!/usr/bin/python

import httplib, urllib
import sys

if len(sys.argv) < 1:
    print("please supply a message")
    sys.exit(1)

params = urllib.urlencode({'message':sys.argv[1]})
headers = {"Content-type":"application/x-www-form-urlencoded",
           "Accept": "text/plain"}
conn = httplib.HTTPConnection("localhost", 8080)
conn.request("POST", '/candlepin/helloworld', params, headers)
#response = conn.getresponse()
#print response.status, response.reason
#rsp = response.read()
conn.close()
#print rsp

response = urllib.urlopen('http://localhost:8080/candlepin/helloworld')
rsp = response.read()
print(rsp)
