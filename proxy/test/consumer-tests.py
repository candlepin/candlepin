import unittest
import httplib, urllib
import simplejson as json

from testfixture import CandlepinTests
from candlepinapi import *

class ConsumerTests(CandlepinTests):

    def setUp(self):
        CandlepinTests.setUp(self)

    def test_list_cert_serials(self):
        result = self.cp.getCertificateSerials(self.uuid)
        self.assertTrue('serial' in result)
        serials = result['serial']
        for serial in serials:
            self.assertTrue('serial' in serial)
        # TODO: Could use some more testing here once entitlement certs
        # are actually being generated.

    def test_list_certs(self):
        result = self.cp.getCertificates(self.uuid)
        print result
        self.assertTrue('cert' in result)
        cert_list = result['cert']
        for cert in cert_list:
            self.assert_cert_struct(cert)
        # TODO: Could use some more testing here once entitlement certs
        # are actually being generated.

    def test_uuid(self):
        self.assertTrue(self.uuid != None)

    def test_bind_by_entitlement_pool(self):
        # First we list all pools available to this consumer:
        virt_host = 'virtualization_host'
        results = self.cp.getPools(consumer=self.uuid)
        pools = {}
        for pool in results['pool']:
            pools[pool['productId']] = pool
        self.assertTrue(virt_host in pools)

        # Request a virtualization_host entitlement:
        result = self.cp.bindPool(self.uuid, pools[virt_host]['id'])
        print result
        self.assertTrue('id' in result)
        self.assertEquals(virt_host, result['pool']['productId'])

        # Now list consumer's entitlements:
        result = self.cp.getEntitlements(self.uuid)
        print "Consumer's entitlements:"
        print result

    # Just need to request a product that will fail, for now creating a virt
    # system and trying to give it virtualization_host:
    def test_failed_bind_by_entitlement_pool(self):
        # Create a virt system:
        virt_uuid = self.create_consumer(consumer_type="virt_system")

        # Get pool ID for virtualization_host:
        virt_host = 'virtualization_host'
        results = self.cp.getPools(product=virt_host)
        print("Virt host pool: %s" % results)
        pool_id = results['pool']['id']

        # Request a virtualization_host entitlement:
        try:
            self.cp.bindPool(virt_uuid, pool_id)
            self.fail("Shouldn't have made it here.")
        except CandlepinException, e:
            self.assertEquals('rulefailed.virt.ents.only.for.physical.systems',
                    e.response.read())
            self.assertEquals(403, e.response.status)

        # Now list consumer's entitlements:
        result = self.cp.getEntitlements(virt_uuid)
        self.assertTrue(result is None)

    def test_bind_by_product(self):
        # Request a monitoring entitlement:
        result = self.cp.bindProduct(self.uuid, 'monitoring')
        print result
        self.assertTrue('id' in result)
        self.assertEquals('monitoring', result['pool']['productId'])

        # Now list consumer's entitlements:
        result = self.cp.getEntitlements(self.uuid)
        print result

    def test_unbind_all_single(self):
        pools = self.cp.getPools(consumer=self.uuid)
        pool = pools['pool'][0]

        self.cp.bindPool(self.uuid, pool['id'])

        # Now unbind it
        self.cp.unBindAll(self.uuid)

        self.assertEqual(None, self.cp.getEntitlements(self.uuid))

    def test_unbind_all_multi(self):
        pools = self.cp.getPools(consumer=self.uuid)['pool']

        if len(pools) > 1:
            for pool in pools:
                self.cp.bindPool(self.uuid, pool['id'])

            # Unbind them all
            self.cp.unBindAll(self.uuid)
            self.assertEqual(None, self.cp.getEntitlements(self.uuid))

    def test_list_pools(self):
        pools = self.cp.getPools(consumer=self.uuid, product="monitoring")
        self.assertEquals(1, len(pools))

    def test_list_pools_for_bad_objects(self):
        self.assertRaises(Exception, self.cp.getPools, consumer='blah')
        self.assertRaises(Exception, self.cp.getPools, owner='-1')

    def test_list_pools_for_bad_query_combo(self):
        # This isn't allowed, should give bad request.
        self.assertRaises(Exception, self.cp.getPools, consumer=self.uuid, owner=1)

