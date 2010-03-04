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
        result = self.cp.syncCertificates(self.uuid, []) 
        print result
