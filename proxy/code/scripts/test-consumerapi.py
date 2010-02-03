#!/usr/bin/python
# 
# Test creation of a consumer and consumption of an entitlement.
#

import httplib, urllib
import sys
import simplejson as json
import base64

# POST new user
print("create consumer")
info = {
        "type": "system",
        }
#    "parent": "",
#    "type":"system", 
#    "metadata": {
#        "entry":[
#            {
#                "key":"arch", 
#                 "value":"i386"
#            },
#            {
#                "key":"cpu", 
#                "value": "Intel"
#            }]
#    }
#}
params = {"type_label": 'system'}
print params
headers = {"Content-type": "application/json",
           "Accept": "application/json"}
conn = httplib.HTTPConnection("localhost", 8080)
conn.request("POST", '/candlepin/consumer/', urllib.urlencode(params), headers)
response = conn.getresponse()
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
print("created consumer: %s" % rsp)
conn.close()

consumer_uuid = json.loads(rsp)['uuid']
print("Consumer UUID: %s" % consumer_uuid)

# Request list of certificates:
params = {}
headers = {"Content-type": "application/json",
           "Accept": "application/json"}
conn = httplib.HTTPConnection("localhost", 8080)
conn.request("POST", '/candlepin/consumer/%s/certificates' % consumer_uuid,
        urllib.urlencode(params), headers)
response = conn.getresponse()
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
print("certificates: %s" % rsp)
conn.close()

## GET list of consumers
#response = urllib.urlopen('http://localhost:8080/candlepin/consumer/')
#rsp = response.read()
#print("list of consumers: %s" % rsp)

## GET candlepin user
#response = urllib.urlopen('http://localhost:8080/candlepin/consumer/candlepin')
#rsp = response.read()
#print("get: %s" % rsp)

## GET candlepin user
#response = urllib.urlopen('http://localhost:8080/candlepin/consumer/info')
#rsp = response.read()
#print("get info: %s" % rsp)

##print("delete consumer")
##conn = httplib.HTTPConnection("localhost", 8080)
##conn.request("DELETE", '/candlepin/consumer/')
##response = conn.getresponse()
##
##print("Status: %d Response: %s" % (response.status, response.reason))
##conn.close()

##print("delete product from consumer")
##conn.request("DELETE", '/candlepin/consumer/%s/product/%s' % ())
