#!/usr/bin/python

import httplib
import urllib
import urllib2
import sys
import simplejson as json
import os



class Rest(object):
    def __init__(self, hostname="localhost", port="8080", api_url="/candlepin", cert_file=None, key_file=None, debug=None):
        self.hostname = hostname
        self.port = port
        self.api_url = api_url
        self.baseurl = "http://%s:%s/%s" % (self.hostname, self.port, self.api_url)
        self.json_headers = {"Content-type":"application/json",
                             "Accept": "application/json"}
        self.text_headers = {"Content-type":"text/plain",
                             "Accept": "text/plain"}

        self.headers = {"json": self.json_headers,
                        "text": self.text_headers}

        self.cert_file = cert_file
        self.key_file = key_file
        self.debug = debug

        # default content type
        self.content_type = None

    def _request(self, http_type, path, data=None, content_type=None):
        if content_type is None and self.content_type:
            content_type = self.content_type

        if self.cert_file:
	        conn = httplib.HTTPSConnection(self.hostname, self.port, key_file = self.key_file, cert_file = self.cert_file)
        else:
            conn = httplib.HTTPConnection(self.hostname, self.port)

        if self.debug:
            conn.set_debuglevel(self.debug)
        url_path = "%s%s" % (self.api_url, path)
        full_url = "%s:%s%s" % (self.hostname, self.port, self.api_url)
        if self.debug:
            print "url: %s" % full_url
        conn.request(http_type, url_path, body=self.marshal(data, content_type), headers=self.headers[content_type])
        response = conn.getresponse()
        if response.status not in  [200, 204]:
            raise Exception("%s - %s" % (response.status, response.reason))
        rsp = response.read()
        
        if self.debug:
            print "response status: %s" % response.status
            print "response reason: %s" % response.reason

        if self.debug and self.debug >2:
            print "output: %s" % rsp

        if rsp != "":
            return self.demarshal(rsp, content_type)
        return None

    def get(self, path, content_type="json"):
        return self._request("GET", path, content_type=content_type)

    def head(self, path, content_type="json"):
        return self._request("HEAD", path, content_type=content_type)

    def post(self, path, data="", content_type="json"):
        return self._request("POST", path, data=data, content_type=content_type)

    def put(self, path, data="", content_type="json"):
        return self._request("PUT", path, data=data, content_type=content_type)

    def delete(self, path, content_type="json"):
        return self._request("DELETE", path, content_type=content_type)

    def marshal(self,data, format):
        if format == "json":
            return json.dumps(data)
        return data

    def demarshal(self, data, format):
        if format == "json":
            return json.loads(data)
        return data

class CandlePinApi:
    def __init__(self, hostname="localhost", port="8080", api_url="/candlepin", cert_file=None, key_file=None, debug=None):
        self.rest = Rest(hostname=hostname, port=port, api_url=api_url, cert_file=cert_file, key_file=key_file, debug=debug)

    def registerConsumer(self, userid, password, name, hardware=None, products=None):
        path = "/consumer"


        entrys = []
        print hardware
        for key in hardware:
            entrys.append({'key': key,
                          'value': hardware[key]})

        # Don't think this is used yet, and definitely shouldn't
        # be going into the entrys map:
        #if products is not None:
        #    for key in products:
        #        entrys.append({'key':key,
        #                       'value':products[key]})
                              
        consumer = {
            "type": {'label':"system"},
            "name": name,
            "facts":{
                "metadata": {
                    "entry": entrys
                    },
                }
            }


        blob = self.rest.post(path, data=consumer)
        return blob

    def unRegisterConsumer(self, username, password, consumer_uuid):
        path = "/consumer/%s" % consumer_uuid
        blob = self.rest.delete(path)
        return blob

    def bindProduct(self, consumer_uuid, product_label):
        path = "/entitlement/consumer/%s/product/%s" % (consumer_uuid, product_label)
        blob = self.rest.post(path)
        return blob

    def bindPool(self, consumer_uuid, pool_id):
        path = "/entitlement/consumer/%s/pool/%s" % (consumer_uuid, pool_id)
        blob = self.rest.post(path)
        return blob

    def bindRegToken(self, consumer_uuid, regtoken):
        path = "/entitlement/consumer/%s/token/%s" % (consumer_uuid, regtoken)
        blob = self.rest.post(path)
        return blob

    def unBindAll(self, consumer_uuid):
        path = "/entitlement/consumer/%s" % consumer_uuid
        blob = self.rest.delete(path)
        return blob

    def unBindBySerialNumbers(self, consumer_uuid, serial_number_list):
        path = "/entitlement/consumer/%s/%s" % (consumer_uuid, ','.join(serial_number_list))
        blob = self.rest.delete(path)
        return blob

    def syncCertificates(self, consumer_uuid, certificate_list):
        path = "/consumer/%s/certificates" % consumer_uuid
        return self.rest.post(path,data=certificate_list)

    def getPools(self, consumer_uuid):
        path = "/pool/consumer/%s" % consumer_uuid
        return self.rest.get(path)

    def getEntitlements(self, consumer_uuid):
        path = "/entitlement/consumer/%s" % consumer_uuid
        return self.rest.get(path)

    def getProducts(self):
        path = "/product/"
        return self.rest.get(path)



if __name__ == "__main__":

    cp = CandlePinApi(hostname="localhost", port="8080", api_url="/candlepin", debug=10)
    ret = cp.registerConsumer("whoever", "doesntmatter", "some system", {'arch':'i386', 'cpu':'intel'}, {'os':'linux', 'release':'4.2'})
    print ret
    print cp.unRegisterConsumer("dontuser", "notreallyapassword", ret['uuid'])

    ret =  cp.registerConsumer("whoever", "doesntmatter", "some system", {'arch':'i386', 'cpu':'intel'}, {'os':'linux', 'release':'4.2'})

    # broken atm
    print cp.bindProduct(ret['uuid'], "monitoring")

    uuid = ret['uuid']
    ret = cp.syncCertificates(uuid, [])
    print ret

#    ret = cp.unBindAll(uuid)
#    print ret

    ret = cp.getEntitlementPools(uuid)
    print ret

    ret = cp.unBindAll(uuid)
    print ret
