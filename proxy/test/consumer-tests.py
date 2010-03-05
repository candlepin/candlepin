import unittest
import httplib, urllib
import simplejson as json

from testfixture import CandlepinTests
from candlepinapi import Rest, CandlePinApi

class ConsumerTests(CandlepinTests):

    def setUp(self):
        CandlepinTests.setUp(self)

    def test_get_certificates(self):
        # TODO: once cert generation is live, need to request entitlements
        # that will get us certs.
        result = self.cp.getCertificates(self.uuid) 

        # Verify the JSON structure:
        self.assertTrue("cert" in result)
        cert_list = result['cert']
        self.assertEquals(2, len(cert_list))
        cert1 = cert_list[1]
        self.assertTrue("key" in cert1)
        self.assertTrue("cert" in cert1)
        self.assertTrue("serial" in cert1)
        self.assertEquals(3, len(cert1.keys()))

    def test_get_certificates_metadata(self):
        # TODO: once cert generation is live, need to request entitlements
        # that will get us certs.
        result = self.cp.getCertificatesMetadata(self.uuid) 
        print result

        # Verify the JSON structure:
        self.assertTrue("cert" in result)
        cert_list = result['cert']
        self.assertEquals(2, len(cert_list))
        cert1 = cert_list[1]
        self.assertTrue("serial" in cert1)
        self.assertEquals(1, len(cert1.keys()))

