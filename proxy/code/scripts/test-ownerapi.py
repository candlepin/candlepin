#!/usr/bin/python

import httplib, urllib
import sys
import simplejson
import pprint


# test OwnerResourse.list
response = urllib.urlopen('http://localhost:8080/candlepin/owner')
rsp = response.read()
print("get: %s" % rsp)

result = simplejson.loads(rsp)
pprint.pprint(result)


# test OwnerResourse.get
for owner in result['owner']:
    response = urllib.urlopen('http://localhost:8080/candlepin/owner/%s' % owner['uuid'])
    rsp = response.read()
    result = simplejson.loads(rsp)
    pprint.pprint(result)
