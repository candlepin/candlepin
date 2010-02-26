#!/usr/bin/python

import httplib, urllib
import sys
import simplejson as json
import base64

#Base 64 encode the rules, and post them
rules = base64.b64encode("whatever something or another, blah blah")

# http://stackoverflow.com/questions/111945/is-there-anyway-to-do-http-put-in-python
import urllib2
opener = urllib2.build_opener(urllib2.HTTPHandler)
request = urllib2.Request('http://localhost:8080/candlepin/rules', data=rules)
request.add_header('Content-Type', 'application/json')
request.get_method = lambda: 'POST'
url = opener.open(request)

response = urllib.urlopen('http://localhost:8080/candlepin/rules')
rsp = response.read()
rsp = base64.standard_b64decode(rsp)
print("------------")
print(rsp)

# Post a second set of rules
rules2 = base64.b64encode("some different set of rules, not the same as the first")
opener = urllib2.build_opener(urllib2.HTTPHandler)
request = urllib2.Request('http://localhost:8080/candlepin/rules', data=rules2)
request.add_header('Content-Type', 'text/plain')
request.get_method = lambda: 'POST'
url = opener.open(request)

response = urllib.urlopen('http://localhost:8080/candlepin/rules')
rsp = response.read()
rsp = base64.standard_b64decode(rsp)
print("------------")
print(rsp)
