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
	print results
        pools = {}
        for pool in results['pool']:
            pools[pool['productId']] = pool
            print pool
            print
        self.assertTrue(virt_host in pools)

        # Request a virtualization_host entitlement:
        result = self.cp.bindPool(self.uuid, pools[virt_host]['id'])

        # Now list consumer's entitlements:
        result = self.cp.getEntitlements(self.uuid)
        print result

    def test_products(self):
        result = self.cp.getProducts()
        self.assertTrue("product" in result)
        products_list = result['product']
        self.assertEquals(6, len(products_list))

    def test_unbind_all_single(self):
        pools = self.cp.getEntitlementPools(self.uuid) 
        pool = pools['pool'][0]

        self.cp.bindPool(self.uuid, pool['id'])
        
        # Now unbind it
        self.cp.unBindAll(self.uuid)

        self.assertEqual(None, self.cp.getEntitlements(self.uuid))

    def test_unbind_all_multi(self):
        pools = self.cp.getEntitlementPools(self.uuid)['pool']

        if len(pools) > 1:
            for pool in pools:
                self.cp.bindPool(self.uuid, pool['id'])

            # Unbind them all
            self.cp.unBindAll(self.uuid)
            self.assertEqual(None, self.cp.getEntitlements(self.uuid))
