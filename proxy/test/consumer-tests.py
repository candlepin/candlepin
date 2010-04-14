import unittest
import httplib, urllib
import simplejson as json

from testfixture import CandlepinTests
from candlepinapi import *

class ConsumerTests(CandlepinTests):

    def setUp(self):
        CandlepinTests.setUp(self)

    def tearDown(self):
        CandlepinTests.tearDown(self)

    def test_list_cert_serials(self):
        result = self.cp.getCertificateSerials(self.uuid)
        print result
        #self.assertTrue('serial' in result)
        #serials = result['serial']
        for serial in result:
            self.assertTrue('serial' in serial)
        # TODO: Could use some more testing here once entitlement certs
        # are actually being generated.

    def test_list_certs(self):
        self.cp.bindProduct(self.uuid, 'monitoring')
        self.cp.bindProduct(self.uuid, 'virtualization_host')

        cert_list = self.cp.getCertificates(self.uuid)
        self.assertEquals(2, len(cert_list))
        last_key = None
        last_cert = None
        for cert in cert_list:
            print
            print "cert from cert_list"
            print cert
            self.assert_ent_cert_struct(cert)
            print

            if last_key is not None:
                self.assertEqual(last_key, cert['key'])
                self.assertNotEqual(last_cert, cert['cert'])
                self.assertEquals(last_key, self.id_cert['key'])
            last_key = cert['key']
            last_cert = cert['cert']

    def test_list_certs_serial_filtering(self):
        self.cp.bindProduct(self.uuid, 'monitoring')
        self.cp.bindProduct(self.uuid, 'virtualization_host')
        self.cp.bindProduct(self.uuid, 'provisioning')

        cert_list = self.cp.getCertificates(self.uuid)
        self.assertEquals(3, len(cert_list))
        for cert in cert_list:
            print
            print "cert from cert_list"
            print cert
            self.assert_ent_cert_struct(cert)
            print

        serials = [cert_list[0]['serial'], cert_list[1]['serial']]
        cert_list = self.cp.getCertificates(self.uuid, serials)
        self.assertEquals(2, len(cert_list))
