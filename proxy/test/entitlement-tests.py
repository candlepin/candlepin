import unittest
import httplib, urllib
import simplejson as json

from testfixture import CandlepinTests
from candlepinapi import Rest, CandlePinApi

class EntitlementTests(CandlepinTests):

    def setUp(self):
        CandlepinTests.setUp(self)
        self.cp = CandlePinApi(hostname="localhost", port="8080", api_url="/candlepin")

        self.debug = False

        facts_metadata = {
                "a": "1",
                "b": "2",
                "c": "3"}
        self.consumer_uuid = self.cp.registerConsumer("fakeuser", "fakepw", "consumername", hardware=facts_metadata)['uuid']

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
 
    #def test_entitlement_id(self):
    #    # TODO: test not ready yet, need a way to create an entitlement first
    #    self.conn.request("GET", '/candlepin/entitlement/1', json.dumps(self.params), self.headers)
    #    response = self.conn.getresponse()
    #    self.assertResponse(200, response)
    #    rsp = response.read()
    #    entitlement_id = json.loads(rsp)['id']
    #    self.assertEquals('1', entitlement_id)
 
    #def test_entitlement_productId(self):
    #    # TODO: test not ready yet, need a way to create an entitlement first
    #    self.conn.request("GET", '/candlepin/entitlement/1','',  self.headers)
    #    response = self.conn.getresponse()
    #    rsp = response.read()
    #    if self.debug:
    #        print 'response',response
    #        print rsp
    #        print('rsp')
    #    dict_outer = json.loads(rsp)
    #    for key, value in dict_outer.items():
    #        if self.debug:
    #            print key, value
    #            print('\n')
    #            print "product pool =",dict_outer["pool"]
    #            dict_inner = dict_outer["pool"]
    #            for key, value in dict_inner.items():
    #                if self.debug:
    #                    print key,value
    #                    print ('\n')
    #        #WILL EVENTUALLY TEST IF productId is member of Products
    #        entitlement_productId = dict_inner["productId"]
    #        self.assertTrue(entitlement_productId != None)

    def test_entitlement_product_id(self):
        self.conn.request("GET", '/candlepin/product', json.dumps(self.params), self.headers)
        self.response = self.conn.getresponse()
        self.rsp = self.response.read()
        #self.entitlement_id = json.loads(self.rsp)['id']
        if self.debug:
            #print('list of product='+self.rsp)
            print('\n')
        self.assertTrue(self.rsp != None)
