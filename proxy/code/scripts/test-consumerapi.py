#!/usr/bin/python
# 
# Test creation of a consumer and consumption of an entitlement.
#

import httplib, urllib
import sys
import simplejson as json
import base64
import pprint


# POST new user
print("create consumer")
info = {
        "type": "system",
        }

product = {'id':'1', 'name':'FooBarLinux', 'label':'fbl'}
product = {"product":{"id":"1","label":"FooBarLinux","name":"fbl"}}
product = {"id":"1","label":"FooBarLinux","name":"fbl"}

consumer = {
    "type": {'label':"system"},
    "name":'billybob',
#    "consumedProducts": product,
    "facts":{
        "metadata": {
            "entry":[
                {
                    "key":"arch", 
                    "value":"i386"
                    },
                {
                    "key":"cpu", 
                    "value": "Intel"
                }]
            },
    }
}





#params = {"type": 'system'}
#print params
headers = {"Content-type": "application/json",
           "Accept": "application/json"}
conn = httplib.HTTPConnection("localhost", 8080)
conn.request("POST", '/candlepin/consumer/', json.dumps(consumer), headers)
response = conn.getresponse()
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
print("created consumer: %s" % rsp)
conn.close()

consumer_uuid = json.loads(rsp)['uuid']
headers = {"Content-type": "application/json",
           "Accept": "application/json"}
conn = httplib.HTTPConnection("localhost", 8080)
conn.request("GET", '/candlepin/consumer/%s' % consumer_uuid,  urllib.urlencode({}), headers)
response = conn.getresponse()
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
print("GET consumer/%s: %s" % (consumer_uuid, rsp))
conn.close()

pprint.pprint(json.loads(rsp))



#import urllib2
#opener = urllib2.build_opener(urllib2.HTTPHandler)
#request = urllib2.Request('http://localhost:8080/candlepin/consumer', data=json.dumps(params))
#request.add_header('Content-Type', 'application/json')
#request.get_method = lambda: 'POST'
#url = opener.open(request)

#response = urllib.urlopen('http://localhost:8080/candlepin/rules')
#rsp = response.read()
#print("------------")
#print(rsp)




print "calling /candlepin/consumer/%s/certificates"
consumer_uuid = json.loads(rsp)['uuid']
print("Consumer UUID: %s" % consumer_uuid)
print "calling /candlepin/consumer/%s/certificates" % consumer_uuid
print 
print

# Request list of certificates:
params = {}
headers = {"Content-type": "application/json",
           "Accept": "application/json"}
conn = httplib.HTTPConnection("localhost", 8080)
conn.request("GET", '/candlepin/consumer/%s/certificates' % consumer_uuid,
        urllib.urlencode(params), headers)
response = conn.getresponse()
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
print("certificates: %s" % pprint.pprint(json.loads(rsp)))
print json.loads(rsp)
conn.close()

#print "rsp", rsp
#print
#print "json rsp", json.loads(rsp)
#print 

data = json.loads(rsp)
certs = data['certs']
pprint.pprint(certs)
for i in certs:
    buf = i['bundle']
#    print
#    print buf
#    print base64.decodestring(buf)
#    print



#for i in json.loads(rsp)['certs']:
#    print i


## GET list of consumers
response = urllib.urlopen('http://localhost:8080/candlepin/consumer/')
rsp = response.read()
print("list of consumers: %s" % rsp)

## GET candlepin user
#response = urllib.urlopen('http://localhost:8080/candlepin/consumer/candlepin')
#rsp = response.read()
#print("get: %s" % rsp)

## GET candlepin user
#response = urllib.urlopen('http://localhost:8080/candlepin/consumer/info')
#rsp = response.read()
#print("get info: %s" % rsp)


print("delete consumer")

#conn = httplib.HTTPConnection("localhost", 8080)
#conn.request("DELETE", '/candlepin/consumer/%s' % consumer_uuid)
#response = conn.getresponse()
#rsp = response.read()

#print "delete of consumer %s: %s" % (consumer_uuid, rsp)


headers = {"Content-type": "application/json",
           "Accept": "application/json"}
conn = httplib.HTTPConnection("localhost", 8080)
serial_numbers = {"serialNumber":"SerialNumbersAreAwesome-1234"}

serial_numbers = {'certficate_serial_number': [{'serialNumber': 'SerialNumbersAreAwesome-1234'}, {'serialNumber': 'A different serial Number'}]}
print json.dumps(serial_numbers)
urlbuf = '/candlepin/consumer/%s/certificates/' % consumer_uuid
print urlbuf
conn.request("POST", urlbuf, json.dumps(serial_numbers), headers)
response = conn.getresponse()
print("Status: %d Response: %s" % (response.status, response.reason))
rsp = response.read()
print("created consumer: %s" % rsp)
conn.close()

##print("Status: %d Response: %s" % (response.status, response.reason))
##conn.close()

##print("delete product from consumer")
##conn.request("DELETE", '/candlepin/consumer/%s/product/%s' % ())
