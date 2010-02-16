import unittest

import httplib, urllib
import simplejson as json

from testfixture import CandlepinTests

class EntitlementTests(CandlepinTests):

    def setUp(self):
        conn = httplib.HTTPConnection("localhost", 8080)
        # Create a consumer and store it's UUID:
        consumer_type = {"label": "system"}
        facts_metadata = {"entry": [
            {"key": "a", "value": "1"},
            {"key": "b", "value": "2"},
            {"key": "total_guests", "value": "0"},
        ]}
        facts = {"metadata": facts_metadata}
        params = {"type": consumer_type, "name": "helloworld", "facts": facts}
        headers = {"Content-type": "application/json",
                   "Accept": "application/json"}
        conn.request("POST", '/candlepin/consumer/', json.dumps(params), headers)
        response = conn.getresponse()
        rsp = response.read()
        conn.close()
        self.consumer_uuid = json.loads(rsp)['uuid']

    def test_something(self):
        pass
