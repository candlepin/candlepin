import unittest
import httplib, urllib
import simplejson as json
import base64
import os

from urllib import urlopen

from certs import SPACEWALK_PUBLIC_CERT

CANDLEPIN_SERVER = "http://localhost:8080/candlepin"
CERTS_UPLOADED = False

class CandlepinTests(unittest.TestCase):

    def setUp(self):

        # Upload the test cert to populate Candlepin db. Only do this
        # once per test suite run.
        rsp = urlopen("%s/certificate" % CANDLEPIN_SERVER).read()
        if rsp.strip() == "":
            print("Cert upload required.")

            cert = self.__read_cert()
            encoded_cert = base64.b64encode(cert)
            params = {"base64cert": encoded_cert}
            headers = {"Content-type": "application/json",
                    "Accept": "application/json"}

            conn = httplib.HTTPConnection("localhost", 8080)
            conn.request("POST", '/candlepin/certificate/', json.dumps(encoded_cert), headers)
            response = conn.getresponse()
            print("Status: %s Reason: %s" % (response.status, response.reason))
            rsp = response.read()
            conn.close()


            rsp = urlopen("%s/certificate" % CANDLEPIN_SERVER).read()
            self.assertTrue(rsp.strip() != "")

    def assertResponse(self, expected_status, response):
        if response.status != expected_status:
            self.fail("Status: %s Reason: %s" % (response.status, response.reason))

    def __read_cert(self, cert_path='code/scripts/spacewalk-public.cert'):
        root = os.abspath(os.join(__file__, '..'))
        return open(os.join(root, cert_path), 'rb').read()


## GET see if there's a certificate
#response = urllib.urlopen('http://localhost:8080/candlepin/certificate')
#rsp = response.read()
#print("get: %s" % rsp)

##"""
## POST upload a certificate
#print("upload certificate")


