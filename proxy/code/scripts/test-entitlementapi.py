#!/usr/bin/python

import httplib, urllib
import sys
import simplejson

params = urllib.urlencode({'name':'entitlement'})
headers = {"Content-type":"application/json",
           "Accept": "application/json"}
conn = httplib.HTTPConnection("localhost", 8080)
conn.request("POST", '/candlepin/entitlement', params, headers)
response = conn.getresponse()
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
conn.close()
print("create: %s" % rsp)

response = urllib.urlopen('http://localhost:8080/candlepin/entitlement/candlepin')
rsp = response.read()
print("get: %s" % rsp)

response = urllib.urlopen('http://localhost:8080/candlepin/entitlement/list')
rsp = response.read()
print("list: %s" % rsp)
