import unittest
import httplib, urllib
import simplejson as json
import base64

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
            cert = SPACEWALK_PUBLIC_CERT
            encoded_cert = base64.b64encode(cert)
            params = {"base64cert": encoded_cert}
            headers = {"Content-type": "application/json",
                    "Accept": "application/json"}

            conn = httplib.HTTPConnection("localhost", 8080)
            conn.request("POST", '/candlepin/certificate/', json.dumps(encoded_cert), headers)
            response = conn.getresponse()
            rsp = response.read()
            conn.close()


            rsp = urlopen("%s/certificate" % CANDLEPIN_SERVER).read()
            self.assertTrue(rsp.strip() != "")


## GET see if there's a certificate
#response = urllib.urlopen('http://localhost:8080/candlepin/certificate')
#rsp = response.read()
#print("get: %s" % rsp)

##"""
## POST upload a certificate
#print("upload certificate")


