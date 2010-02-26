#!/usr/bin/python

import httplib, urllib
import sys
import simplejson as json
import base64

# Read the files from the file, and encode them
filename = sys.argv[1]
f = open(filename)
rules = f.read()
rules = base64.b64encode(rules)

# http://stackoverflow.com/questions/111945/is-there-anyway-to-do-http-put-in-python
import urllib2
opener = urllib2.build_opener(urllib2.HTTPHandler)
request = urllib2.Request('http://localhost:8080/candlepin/rules', data=rules)
request.add_header('Content-Type', 'text/plain')
request.get_method = lambda: 'POST'
url = opener.open(request)

response = urllib.urlopen('http://localhost:8080/candlepin/rules')
rsp = response.read()
rsp = base64.standard_b64decode(rsp)
print("------------")
print(rsp)

#"""
# GET see if the rules are still there.
response = urllib.urlopen('http://localhost:8080/candlepin/rules')
rsp = response.read()
rsp = base64.standard_b64decode(rsp)
print("------------")
print(rsp)
