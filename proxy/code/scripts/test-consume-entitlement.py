#!/usr/bin/python

import httplib, urllib
import sys
import simplejson as json
import base64

conn = httplib.HTTPConnection("localhost", 8080)

# POST new user
print("Creating a new consumer:")

consumer_type = {"label": "system"}
params = {"type": consumer_type, "name": "helloworld"}
headers = {"Content-type": "application/json",
           "Accept": "application/json"}
conn.request("POST", '/candlepin/consumer/', json.dumps(params), headers)
response = conn.getresponse()
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
print("Created consumer: %s" % rsp)
conn.close()
consumer_uuid = json.loads(rsp)['uuid']
print("Consumer UUID: %s" % consumer_uuid)


## Entitle consumer for rhel-server:
#print("Entitling consumer for monitoring")
#conn = httplib.HTTPConnection("localhost", 8080)
#params = urllib.urlencode({
#    "consumer_uuid": consumer_uuid,
#    "product_id": "monitoring"
#})
#headers = {"Content-type":"application/json",
#           "Accept": "application/json"}
#conn.request("POST", '/candlepin/entitlement/entitle', params, headers)
#response = conn.getresponse()
#print("Status: %d Response: %s" % (response.status, response.reason))
#rsp = response.read()
#conn.close()
#print("Entitlement claimed: %s" % rsp)

