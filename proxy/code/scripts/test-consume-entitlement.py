#!/usr/bin/python

import httplib, urllib
import simplejson as json

conn = httplib.HTTPConnection("localhost", 8080)
print

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
print


# Consume a virtualization_host entitlement:
print("Consuming virtualization host entitlement:")
conn = httplib.HTTPConnection("localhost", 8080)
params = urllib.urlencode({
})
headers = {"Content-type":"application/json",
           "Accept": "application/json"}
path = '/candlepin/entitlement/consumer/%s/product/%s' % \
        (consumer_uuid, "virtualization_host")
conn.request("POST", path, params, headers)
response = conn.getresponse()
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
conn.close()
print("Entitlement claimed: %s" % rsp)


# Get my certificates:
print("Getting certificates:")
conn = httplib.HTTPConnection("localhost", 8080)
params = urllib.urlencode({})
headers = {"Content-type":"application/json",
           "Accept": "application/json"}
path = '/candlepin/consumer/%s/certificates' % \
        (consumer_uuid)
conn.request("GET", path, params, headers)
response = conn.getresponse()
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
conn.close()
print("Response:")
print(rsp)
#results = json.loads(rsp)
#for x in results['clientCertificate']:
#    print x
#    print
