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
        response = self.cp.registerConsumer("fakeuser", "fakepw",
                "consumername", hardware=facts_metadata)
        self.uuid = response['uuid']

    def test_uuid(self):
        self.assertTrue(self.uuid != None)
   
    def test_certificates(self):
        # Assumes consumer has an entitlement granted to a product:
        result = self.cp.syncCertificates(self.uuid, [])
        certs = result['clientCertStatus']
        self.assertTrue(certs != None)

    def test_bind_by_entitlement_pool(self):
        # First we list all entitlement pools available to this consumer:
        virt_host = 'virtualization_host'
        results = self.cp.getEntitlementPools(self.uuid)
        pools = {}
        for pool in results['entitlementPool']:
            pools[pool['productId']] = pool
            print pool
            print
        self.assertTrue(virt_host in pools)

        # Request a virtualization_host entitlement:
        result = self.cp.bindPool(self.uuid, pools[virt_host]['id'])

        # Now list consumer's entitlements:
        result = self.cp.getEntitlements(self.uuid)
        print result



    # TODO: Not really testing anything yet:
    #def test_entitlements(self):
    #    self.conn.request("GET", '/candlepin/entitlement', json.dumps(self.params), self.headers)
    #    response = self.conn.getresponse()
    #    rsp = response.read()
    #    if self.debug:
    #        print('\n')
    #        print('list of entitlements:'+ rsp)
    #        print('\n')
    #    if rsp != None:
    #        pass
    #    else:
    #        assert False 
 
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

    def test_products(self):
        result = self.cp.getProducts()
        self.assertTrue("product" in result)
        products_list = result['product']
        self.assertEquals(6, len(products_list))
