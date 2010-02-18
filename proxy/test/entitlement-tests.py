import unittest

import httplib, urllib
import simplejson as json

from testfixture import CandlepinTests

class EntitlementTests(CandlepinTests):

 def setUp(self):
        self.debug = False
	self.conn = httplib.HTTPConnection("localhost", 8080)
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
	#print('conn.request='+self.rsp) 

 def test_uuid(self):
        if self.consumer_uuid != None:
	    pass
	else:
	    assert False
   
 def test_dbid(self):
        self.conn.request("GET", '/candlepin/consumer/'+self.consumer_uuid+'/certificates', json.dumps(self.params), self.headers)
        response = self.conn.getresponse()
        rsp = response.read()
        consumer_cert = json.loads(rsp)['certs']
   	if consumer_cert != None:
	    pass
	else:
	    assert False		

 def test_entitlement(self):
        self.conn.request("GET", '/candlepin/entitlement', json.dumps(self.params), self.headers)
        response = self.conn.getresponse()
        rsp = response.read()
	if self.debug:
	    print('\n')
	    print('list of entitlements:'+ rsp)
	    print('\n')
   	if rsp != None:
	    pass
	else:
	    assert False 
 
 def test_entitlement_id(self):
        self.conn.request("GET", '/candlepin/entitlement/1', json.dumps(self.params), self.headers)
        response = self.conn.getresponse()
        rsp = response.read()
        entitlement_id = json.loads(rsp)['id']
   	if entitlement_id == '1':
	    pass
	else:
	    assert False
 
 def test_entitlement_productId(self):
        self.conn.request("GET", '/candlepin/entitlement/1','',  self.headers)
        response = self.conn.getresponse()
        rsp = response.read()
	if self.debug:
	    print 'response',response
	    print rsp
	    print('rsp')
	dict_outer = json.loads(rsp)
        for key, value in dict_outer.items():
	    if self.debug:
	        print key, value
	        print('\n')
	        print "product pool =",dict_outer["pool"]
            dict_inner = dict_outer["pool"]
            for key, value in dict_inner.items():
	        if self.debug:
		    print key,value
		    print ('\n')
	#WILL EVENTUALLY TEST IF productId is member of Products
        entitlement_productId = dict_inner["productId"]
   	if entitlement_productId != None:
	    pass
	else:
	    assert False

 def test_entitlement_product_id(self):
        self.conn.request("GET", '/candlepin/product', json.dumps(self.params), self.headers)
        self.response = self.conn.getresponse()
        self.rsp = self.response.read()
        #self.entitlement_id = json.loads(self.rsp)['id']
	if self.debug:
	    #print('list of product='+self.rsp)
	    print('\n')
   	if self.rsp != None:
	    pass
	else:
	    assert False
