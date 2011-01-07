# A proxy interface to initiate and interact communication with Unified Entitlement Platform server such as candlepin.
#
# Copyright (c) 2010 Red Hat, Inc.
#
# Authors: Pradeep Kilambi <pkilambi@redhat.com>
#
# This software is licensed to you under the GNU General Public License,
# version 2 (GPLv2). There is NO WARRANTY for this software, express or
# implied, including the implied warranties of MERCHANTABILITY or FITNESS
# FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
# along with this software; if not, see
# http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
#
# Red Hat trademarks are not licensed under GPLv2. No permission is
# granted to use or replicate Red Hat trademarks that are incorporated
# in this software or its documentation.
#

import sys
import locale
import httplib
import simplejson as json
import base64
import os
from M2Crypto import SSL, httpslib
import logging
from config import initConfig

class NullHandler(logging.Handler):
    def emit(self, record):
        pass

h = NullHandler()
logging.getLogger("rhsm").addHandler(h)

log = logging.getLogger(__name__)

config = initConfig()


class ConnectionException(Exception):
    pass


class ConnectionSetupException(ConnectionException):
    pass


class BadCertificateException(ConnectionException):
    """ Thrown when an error parsing a certificate is encountered. """

    def __init__(self, cert_path):
        """ Pass the full path to the bad certificate. """
        self.cert_path = cert_path


class RestlibException(ConnectionException):

    def __init__(self, code, msg=""):
        self.code = code
        self.msg = msg

    def __str__(self):
        return self.msg


class NetworkException(ConnectionException):

    def __init__(self, code):
        self.code = code

class RhsmProxyHTTPSConnection(httpslib.ProxyHTTPSConnection):
    # 2.7 httplib expects to be able to pass a body argument to
    # endheaders, which the m2crypto.httpslib.ProxyHTTPSConnect does
    # not support
    def endheaders(self, body=None):
        if not self._proxy_auth:
            self._proxy_auth = self._encode_auth()

        if body:
            httpslib.HTTPSConnection.endheaders(self, body)
        else:
            httpslib.HTTPSConnection.endheaders(self)
    #        httpslib.HTTPSConnection.endheaders(self, body)

        

class Restlib(object):
    """
     A wrapper around httplib to make rest calls easier
    """

    def __init__(self, host, ssl_port, apihandler,
            username=None, password=None,
            proxy_hostname=None, proxy_port=None,
            proxy_user=None, proxy_password=None,
            cert_file=None, key_file=None,
            ca_dir=None, insecure=False, ssl_verify_depth=1):
        self.host = host
        self.ssl_port = ssl_port
        self.apihandler = apihandler
        self.headers = {"Content-type": "application/json",
                        "Accept": "application/json",
                        "Accept-Language": locale.getdefaultlocale()[0].lower().replace('_', '-')}
        self.cert_file = cert_file
        self.key_file = key_file
        self.ca_dir = ca_dir
        self.insecure = insecure
        self.username = username
        self.password = password
        self.ssl_verify_depth = ssl_verify_depth
        self.proxy_hostname = proxy_hostname
        self.proxy_port = proxy_port
        self.proxy_user = proxy_user
        self.proxy_password = proxy_password

        # Setup basic authentication if specified:
        if username and password:
            encoded = base64.b64encode(':'.join((username, password)))
            basic = 'Basic %s' % encoded
            self.headers['Authorization'] = basic

    def _load_ca_certificates(self, context):
        try:
            for cert_file in os.listdir(self.ca_dir):
                if cert_file.endswith(".pem"):
                    cert_path = os.path.join(self.ca_dir, cert_file)
                    log.info("loading ca certificate '%s'" % cert_path)
                    res = context.load_verify_info(cert_path)

                    if res == 0:
                        raise BadCertificateException(cert_path)
        except OSError, e:
            raise ConnectionSetupException(e.strerror)


    def _request(self, request_type, method, info=None):
        handler = self.apihandler + method
        context = SSL.Context("sslv3")
        if self.ca_dir != None:
            log.info('loading ca pem certificates from: %s', self.ca_dir)
            self._load_ca_certificates(context)
        log.info('work in insecure mode ?:%s', self.insecure)
        if not self.insecure: #allow clients to work insecure mode if required..
            context.set_verify(SSL.verify_fail_if_no_peer_cert, self.ssl_verify_depth)
        if self.cert_file:
            context.load_cert(self.cert_file, keyfile=self.key_file)

        if self.proxy_hostname and self.proxy_port:
            log.info("using proxy %s:%s" % (self.proxy_hostname, self.proxy_port))
            conn = RhsmProxyHTTPSConnection(self.proxy_hostname, self.proxy_port,
                                            username=self.proxy_user,
                                            password=self.proxy_password,
                                            ssl_context=context)
            # this connection class wants the full url 
            handler = "https://%s:%s%s" % (self.host, self.ssl_port, handler)
            log.info("handler: %s" % handler)
        else:
            conn = httpslib.HTTPSConnection(self.host, self.ssl_port, ssl_context=context)

        conn.request(request_type, handler,
                     body=json.dumps(info), 
                     headers=self.headers)

        response = conn.getresponse()
        result = {
            "content": response.read(),
            "status": response.status}
        #TODO: change logging to debug.
       # log.info('response:' + str(result['content']))
        log.info('status code: ' + str(result['status']))
        self.validateResponse(result)
        if not len(result['content']):
            return None
        return json.loads(result['content'])

    def validateResponse(self, response):
        if str(response['status']) not in ["200", "204"]:
            parsed = {}
            try:
                parsed = json.loads(response['content'])
            except Exception, e:
                log.exception(e)
                raise NetworkException(response['status'])

            raise RestlibException(response['status'],
                    parsed['displayMessage'])

    def request_get(self, method):
        return self._request("GET", method)

    def request_post(self, method, params=""):
        return self._request("POST", method, params)

    def request_head(self, method):
        return self._request("HEAD", method)

    def request_put(self, method, params=""):
        return self._request("PUT", method, params)

    def request_delete(self, method):
        return self._request("DELETE", method)


class UEPConnection:
    """
    Class for communicating with the REST interface of a Red Hat Unified
    Entitlement Platform.
    """

    def __init__(self,
            host=config.get('server', 'hostname'),
            ssl_port=int(config.get('server', 'port')),
            handler=config.get('server', 'prefix'),
            proxy_hostname=config.get('server', 'proxy_hostname'),
            proxy_port=config.get('server', 'proxy_port'),
            proxy_user=config.get('server', 'proxy_user'),
            proxy_password=config.get('server', 'proxy_password'),
            username=None, password=None,
            cert_file=None, key_file=None,
            insecure=None):
        """
        Two ways to authenticate:
            - username/password for HTTP basic authentication. (owner admin role)
            - uuid/key_file/cert_file for identity cert authentication.
              (consumer role)

        Must specify one method of authentication or the other, not both.
        """
        self.host = host
        self.ssl_port = ssl_port
        self.handler = handler
        
        self.proxy_hostname = proxy_hostname
        self.proxy_port = proxy_port
        self.proxy_user = proxy_user
        self.proxy_password = proxy_password

        self.cert_file = cert_file
        self.key_file = key_file
        self.username = username
        self.password = password

        self.ca_cert_dir = config.get('server', 'ca_cert_dir')
        self.ssl_verify_depth = int(config.get('server', 'ssl_verify_depth'))

        self.insecure = insecure
        if insecure is None:
            self.insecure = False
            config_insecure = int(config.get('server', 'insecure'))
            if config_insecure:
                self.insecure = True

        using_basic_auth = False
        using_id_cert_auth = False

        if username and password:
            using_basic_auth = True
        elif cert_file and key_file:
            using_id_cert_auth = True

        if using_basic_auth and using_id_cert_auth:
            raise Exception("Cannot specify both username/password and "
                    "cert_file/key_file")
        if not (using_basic_auth or using_id_cert_auth):
            raise Exception("Must specify either username/password or "
                    "cert_file/key_file")

        # initialize connection
        if using_basic_auth:
            self.conn = Restlib(self.host, self.ssl_port, self.handler,
                    username=self.username, password=self.password,
                    proxy_hostname=self.proxy_hostname, proxy_port=self.proxy_port,
                    proxy_user=self.proxy_user, proxy_password=self.proxy_password,
                    ca_dir=self.ca_cert_dir, insecure=self.insecure,
                    ssl_verify_depth=self.ssl_verify_depth)
            log.info("Using basic authentication as: %s" % username)
        else:
            self.conn = Restlib(self.host, self.ssl_port, self.handler,
                    cert_file=self.cert_file, key_file=self.key_file,
                    proxy_hostname=self.proxy_hostname, proxy_port=self.proxy_port,
                    proxy_user=self.proxy_user, proxy_password=self.proxy_password,
                    ca_dir=self.ca_cert_dir, insecure=self.insecure,
                    ssl_verify_depth=self.ssl_verify_depth)
            log.info("Using certificate authentication: key = %s, cert = %s, "
                    "ca = %s, insecure = %s" %
                    (self.key_file, self.cert_file, self.ca_cert_dir,
                        self.insecure))

        log.info("Connection Established: host: %s, port: %s, handler: %s" %
                (self.host, self.ssl_port, self.handler))

    def add_ssl_certs(self, cert_file=None, key_file=None):
        self.cert_file = cert_file
        self.key_file = key_file
        self.conn = Restlib(self.host, self.ssl_port, self.handler,
                self.cert_file, self.key_file, self.ca_cert_dir, self.insecure)

    def shutDown(self):
        self.conn.close()
        log.info("remote connection closed")

    def ping(self, username=None, password=None):
        return self.conn.request_get("/status/")

    def registerConsumer(self, name="unknown", type="system", facts={}):
        """
        Creates a consumer on candlepin server
        """
        params = {"type": type,
                  "name": name,
                  "facts": facts}
        return self.conn.request_post('/consumers/', params)

    def updateConsumerFacts(self, consumer_uuid, facts={}):
        """
        Update a consumers facts on candlepin server
        """
        params = {"facts": facts}
        method = "/consumers/%s" % consumer_uuid
        ret = self.conn.request_put(method, params)
        return ret

    # TODO: username and password not used here
    def getConsumer(self, uuid, username, password):
        """
        Returns a consumer object with pem/key for existing consumers
        """
        method = '/consumers/%s' % uuid
        return self.conn.request_get(method)

    def unregisterConsumer(self, consumerId):
        """
         Deletes a consumer from candlepin server
        """
        method = '/consumers/%s' % consumerId
        return self.conn.request_delete(method)

    def getCertificates(self, consumer_uuid, serials=[]):
        """
        Fetch all entitlement certificates for this consumer.
        Specify a list of serial numbers to filter if desired.
        """
        method = '/consumers/%s/certificates' % (consumer_uuid)
        if len(serials) > 0:
            serials_str = ','.join(serials)
            method = "%s?serials=%s" % (method, serials_str)
        return self.conn.request_get(method)

    def getCertificateSerials(self, consumerId):
        """
        Get serial numbers for certs for a given consumer
        """
        method = '/consumers/%s/certificates/serials' % consumerId
        return self.conn.request_get(method)

    def bindByRegNumber(self, consumerId, regnum, email=None, lang=None):
        """
        Subscribe consumer to a subscription token
        """
        method = "/consumers/%s/entitlements?token=%s" % (consumerId, regnum)
        if email:
            method += "&email=%s" % email
            if not lang:
                lang = locale.getdefaultlocale()[0].lower().replace('_', '-')
            method += "&email_locale=%s" % lang
        return self.conn.request_post(method)

    def bindByEntitlementPool(self, consumerId, poolId, quantity=None):
        """
         Subscribe consumer to a subscription by pool ID.
        """
        method = "/consumers/%s/entitlements?pool=%s" % (consumerId, poolId)
        if quantity:
            method = "%s&quantity=%s" % (method, quantity)
        return self.conn.request_post(method)

    def bindByProduct(self, consumerId, products):
        """
        Subscribe consumer directly to one or more products by their ID.
        This will cause the UEP to look for one or more pools which provide
        access to the given product.
        """
        args = "&".join(["product=" + product.replace(" ", "%20") \
                for product in products])
        method = "/consumers/%s/entitlements?%s" % (str(consumerId), args)
        return self.conn.request_post(method)

    def unbindBySerial(self, consumerId, serial):
        method = "/consumers/%s/certificates/%s" % (consumerId, serial)
        return self.conn.request_delete(method)

    # TODO: not actually used...
    def unbindByEntitlementId(self, consumerId, entId):
        method = "/consumers/%s/entitlements/%s" % (consumerId, entId)
        return self.conn.request_delete(method)

    def unbindAll(self, consumerId):
        method = "/consumers/%s/entitlements" % consumerId
        return self.conn.request_delete(method)

    def getPoolsList(self, consumerId, listAll=False, active_on=None):
        method = "/pools?consumer=%s" % consumerId
        if listAll:
            method = "%s&listall=true" % method
        if active_on:
            method = "%s&activeon=%s" % (method, active_on.strftime("%Y%m%d"))
        results = self.conn.request_get(method)
        return results

    def getPool(self, poolId):
        method = "/pools/%s" % poolId
        return self.conn.request_get(method)

    def getProduct(self, product_id):
        method = "/products/%s" % product_id
        return self.conn.request_get(method)

    def getEntitlementList(self, consumerId):
        method = "/consumers/%s/entitlements" % consumerId
        results = self.conn.request_get(method)
        return results

    def getEntitlement(self, entId):
        method = "/entitlements/%s" % entId
        return self.conn.request_get(method)

    def regenIdCertificate(self, consumerId):
        method = "/consumers/%s" % consumerId
        return self.conn.request_post(method)


