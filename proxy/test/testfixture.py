import unittest
import httplib, urllib
import simplejson as json
import base64
import os

from urllib import urlopen
from candlepinapi import Rest, CandlePinApi

CERTS_UPLOADED = False

# Used to create one consumer for entire test run, as the operation
# can be quite expensive. (cert generation)
CONSUMER_UUID = None
CONSUMER_ID_CERT = None

class CandlepinTests(unittest.TestCase):

    def setUp(self):

        self.cp = CandlePinApi(hostname="localhost", port="8443",
                               api_url="/candlepin", cert_file="./client.crt",
                               key_file="./client.key", username="Spacewalk Public Cert",
                               password="testuserpass")
        #self.cp = CandlePinApi(hostname="localhost", port="8080", api_url="/candlepin")
        #self.cp = CandlePinApi(hostname="localhost", port="8443", 
        #                       api_url="/candlepin", username="Spacewalk Public Cert",
        #                       password="notchecked")

        # TODO: Use CandlePinAPI?
        # Upload the test cert to populate Candlepin db. Only do this
        # once per test suite run.
        #rest = Rest(hostname="localhost", port="8080", api_url="/candlepin")
        #rest = Rest(hostname="localhost", port="8443", api_url="/candlepin", cert_file="./client.crt", key_file="./client.key")
        rest = Rest(hostname="localhost", port="8080", api_url="/candlepin")
        rsp = rest.get("/certificates")
        if not rsp:
            print("Cert upload required.")

            cert = self.__read_cert()
            encoded_cert = base64.b64encode(cert)
            params = {"base64cert": encoded_cert}
            headers = {"Content-type": "application/json",
                    "Accept": "application/json"}

            rest.post("/certificates", json.dumps(encoded_cert))

            rsp = rest.get("/certificates")

            self.assertTrue(rsp.strip() != "")

        global CONSUMER_UUID
        global CONSUMER_ID_CERT
        if not CONSUMER_UUID:
            self.uuid, self.id_cert = self.create_consumer()
            CONSUMER_UUID = self.uuid
            CONSUMER_ID_CERT = self.id_cert
        else:
            print("Reusing consumer: %s" % CONSUMER_UUID)
            self.uuid = CONSUMER_UUID
            self.id_cert = CONSUMER_ID_CERT

    def tearDown(self):
        # Unentitle the consumer here, but don't delete him. (creating consumer
        # is expensive as a result of the ID cert generation)
        self.cp.unBindAll(self.uuid)

    def assertResponse(self, expected_status, response):
        if response.status != expected_status:
            self.fail("Status: %s Reason: %s" % (response.status, response.reason))

    def __read_cert(self, cert_file='code/scripts/spacewalk-public.cert'):
        cert_file = os.path.abspath(os.path.join(__file__, '../..', cert_file))
        return open(cert_file, 'rb').read()

    def create_consumer(self, consumer_type="system"):
        """ 
        Create a consumer.

        NOTE: This call is relatively expensive, as consumer creation also 
        generates an identity certificate.
        """
        facts_metadata = {
                "a": "1",
                "b": "2",
                "c": "3"}

        cp = CandlePinApi(hostname="localhost", port="8080", api_url="/candlepin")
        #cp = CandlePinApi(hostname="localhost", port="8443", api_url="/candlepin")
        response = cp.registerConsumer("Spacewalk Public Cert", "fakepw",
                "consumername", hardware=facts_metadata, 
                consumer_type=consumer_type)
#        print("Consumer created: %s" % response)
        self.assertTrue("uuid" in response)
        self.assertTrue("idCert" in response)
        self.assert_consumer_struct(response)
        CONSUMER_UUID = response['uuid']

        cert = open("client.crt", "w")
        cert.write(response['idCert']['cert'])
        cert.close()

        key = open("client.key", "w")
        key.write(response['idCert']['key'])
        key.close()

        return (response['uuid'], response['idCert'])

    def assert_consumer_struct(self, consumer):
        """ Verify the given dict represents a consumer struct. """
        keys = ['uuid', 'name', 'facts', 'idCert', 'type', 'id']
        self.assertEquals(len(keys), len(consumer.keys()))
        for key in keys:
            self.assertTrue(key in consumer)

    def assert_ent_cert_struct(self, cert):
        """ Verify the given dict represents a consumer struct. """
        keys = ['serial', 'key', 'cert', 'entitlement']
        self.assertEquals(len(keys), len(cert.keys()))
        for key in keys:
            self.assertTrue(key in cert, "missing %s" % key)
        self.assertEquals(type(1), type(cert['serial']))
        self.assertEquals("-----", cert['key'][0:5])
        self.assertEquals("-----", cert['cert'][0:5])

## GET see if there's a certificate
#response = urllib.urlopen('http://localhost:8080/candlepin/certificate')
#rsp = response.read()
#print("get: %s" % rsp)

##"""
## POST upload a certificate
#print("upload certificate")


