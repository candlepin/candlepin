#!/usr/bin/python

import httplib, urllib
import sys
import simplejson as json
import base64

# read and encode the cert
#cert = open('spacewalk-public.cert', 'rb').read()
#encoded_cert = base64.b64encode(cert)

rules = "whatever something or another, blah blah"

# GET see if there's a certificate

#response = urllib.urlopen('http://localhost:8080/candlepin/rules')
#rsp = response.read()
#print("get: %s" % rsp)


# http://stackoverflow.com/questions/111945/is-there-anyway-to-do-http-put-in-python
import urllib2
opener = urllib2.build_opener(urllib2.HTTPHandler)
request = urllib2.Request('http://localhost:8080/candlepin/rules', data=rules)
request.add_header('Content-Type', 'text/plain')
request.get_method = lambda: 'POST'
url = opener.open(request)

response = urllib.urlopen('http://localhost:8080/candlepin/rules')
rsp = response.read()
print("------------")
print(rsp)


rules2 = "some different set of rules, not the same as the first"
opener = urllib2.build_opener(urllib2.HTTPHandler)
request = urllib2.Request('http://localhost:8080/candlepin/rules', data=rules2)
request.add_header('Content-Type', 'text/plain')
request.get_method = lambda: 'POST'
url = opener.open(request)


#"""
# GET see if there's a certificate
response = urllib.urlopen('http://localhost:8080/candlepin/rules')
rsp = response.read()
print("------------")
print(rsp)
#print(base64.standard_b64decode(rsp))
