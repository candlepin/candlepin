#!/usr/bin/python

import httplib
import urllib
import urllib2
import sys
import simplejson as json



class Rest(object):
    def __init__(self, hostname="localhost", port="8080", api_url="/candlepin", debug=None):
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
        self.debug = None

        # default content type
        self.content_type = None

    def _request(self, http_type, path, data=None, content_type=None):
        if content_type is None and self.content_type:
            content_type = self.content_type

        conn = httplib.HTTPConnection(self.hostname, self.port)
        if self.debug:
            conn.set_debuglevel(self.debug)
        url_path = "%s%s" % (self.api_url, path)
        full_url = "%s:%s%s" % (self.hostname, self.port, self.api_url)
        if self.debug:
            print "url: %s" % full_url
        conn.request(http_type, url_path, body=self.marshal(data, content_type), headers=self.headers[content_type])
        response = conn.getresponse()
        rsp = response.read()
        
        if self.debug:
            print "response status: %s" % response.status
            print "response reason: %s" % response.reason

        if self.debug and self.debug >2:
            print "output: %s" % rsp

        return self.demarshal(rsp, content_type)

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
    def __init__(self, hostname="localhost", port="8080", api_url="/candlepin", debug=None):
        self.rest = Rest(hostname=hostname, port=port, api_url=api_url, debug=debug)

    def registerConsumer(self, userid, password, name, hardware=None, products=None):
        path = "/consumer"


        entrys = []
        print hardware
        for key in hardware:
            entrys.append({'key': key,
                          'value': hardware[key]})

        for key in products:
            entrys.append({'key':key,
                           'value':products[key]})
                              
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

if __name__ == "__main__":
    rest = Rest()
    rest.debug = 10
    print rest.get("/rules", content_type="text")


    cp = CandlePinApi(hostname="localhost", port="8080", api_url="/candlepin", debug=None)
    print cp.registerConsumer("whoever", "doesntmatter", "some system", {'arch':'i386', 'cpu':'intel'}, {'os':'linux', 'release':'4.2'})


