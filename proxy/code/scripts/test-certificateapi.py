#!/usr/bin/python

import httplib, urllib
import sys
import simplejson as json
import base64

cert = open('spacewalk-public.cert', 'rb').read()
encoded_cert = base64.b64encode(cert)
print(encoded_cert)

# GET candlepin user
response = urllib.urlopen('http://localhost:8080/candlepin/certificate')
rsp = response.read()
print("get: %s" % rsp)

params = {"base64cert":encoded_cert}
headers = {"Content-type":"application/json",
           "Accept": "application/json"}
# POST new user
print("create consumer")

conn = httplib.HTTPConnection("localhost", 8080)
#conn.request("POST", '/candlepin/certificate/', json.dumps(params), headers)
conn.request("POST", '/candlepin/certificate/', json.dumps(encoded_cert), headers)
response = conn.getresponse()
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
print("created consumer: %s" % rsp)
conn.close()

