import unittest

import httplib, urllib
import simplejson as json

from testfixture import CandlepinTests

class EntitlementTests(CandlepinTests):

 def setUp(self):
        
	#self.conn = httplib.HTTPConnection("localhost", 8080)
	self.conn = httplib.HTTPConnection("statler.usersys.redhat.com", 8080)
        # Create a consumer and store it's UUID:
        consumer_type = {"label": "system"}
        facts_metadata = {"entry": [
            {"key": "a", "value": "1"},
            {"key": "b", "value": "2"},
            {"key": "total_guests", "value": "0"},
        ]}
        facts = {"metadata": facts_metadata}
        self.params = {"type": consumer_type, "name": "helloworld", "facts": facts}
        self.headers = {"Content-type": "application/json",
                   "Accept": "application/json"}
        self.conn.request("POST", '/candlepin/consumer/', json.dumps(self.params), self.headers)
        self.response = self.conn.getresponse()
        self.rsp = self.response.read()
        self.consumer_uuid = json.loads(self.rsp)['uuid']
        self.conn.close()
	#TODO: remove print lines after tests have been added
	print('conn.request='+self.rsp) 

 def test_uuid(self):
        if self.consumer_uuid != None:
	    pass
	else:
	    assert False
   
 def test_dbid(self):
        self.conn.request("GET", '/candlepin/consumer/'+self.consumer_uuid+'/certificates', json.dumps(self.params), self.headers)
        self.response = self.conn.getresponse()
        self.rsp = self.response.read()
        self.consumer_cert = json.loads(self.rsp)['certs']
   	if self.consumer_cert != None:
	    pass
	else:
	    assert False		

