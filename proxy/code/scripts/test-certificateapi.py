#!/usr/bin/python

import httplib, urllib
import sys
import simplejson as json
import base64

# read and encode the cert
cert = open('spacewalk-public.cert', 'rb').read()
encoded_cert = base64.b64encode(cert)

# GET see if there's a certificate
response = urllib.urlopen('http://localhost:8080/candlepin/certificate')
rsp = response.read()
print("get: %s" % rsp)

#"""
# POST upload a certificate
print("upload certificate")

params = {"base64cert":encoded_cert}
headers = {"Content-type": "application/json",
           "Accept": "application/json"}

conn = httplib.HTTPConnection("localhost", 8080)
print("encoded cert: %s" % encoded_cert)
conn.request("POST", '/candlepin/certificate/', json.dumps(encoded_cert), headers)
response = conn.getresponse()
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
print("uploaded certificate: %s" % rsp)
conn.close()
#"""
# GET see if there's a certificate
response = urllib.urlopen('http://localhost:8080/candlepin/certificate')
rsp = response.read()
print("------------")
#print(rsp)
print(base64.standard_b64decode(rsp))
