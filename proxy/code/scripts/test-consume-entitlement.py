#!/usr/bin/python

import httplib, urllib
import sys
import simplejson as json
import base64

conn = httplib.HTTPConnection("localhost", 8080)

# POST new user
print("Creating a new consumer:")

consumer_type = {"label": "system"}
facts_metadata = {"entry": [
    {"key": "a", "value": "1"},
    {"key": "b", "value": "2"},
    {"key": "total_guests", "value": "0"},
]}
facts = {"metadata": facts_metadata}
params = {"type": consumer_type, "name": "helloworld", "facts": facts}
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


# Entitle consumer for rhel-server:
print("Consuming virtualization host entitlement:")
conn = httplib.HTTPConnection("localhost", 8080)
params = urllib.urlencode({
    "consumer_uuid": consumer_uuid,
    "product_label": "virtualization_host"
})
headers = {"Content-type":"application/json",
           "Accept": "application/json"}
conn.request("POST", '/candlepin/entitlement/entitle', params, headers)
response = conn.getresponse()
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
conn.close()
print("Entitlement claimed: %s" % rsp)

