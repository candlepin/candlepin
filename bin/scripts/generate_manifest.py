#!/usr/bin/env python
from __future__ import print_function, division, absolute_import

"""
Generates a manifest from synthetic data using a hosted-test enabled Candlepin instance

This script expected data to be formatted following the API schema, using well-formed API DTOs
wrapped in a dictionary/map with the following keys:

contents: a list of ContentDTO objects to create
products: a list of ProductDTO objects to create
subscriptions: a list of SubscriptionDTO objects to create

The objects will be created in the order in which they appear in each list, and the lists will be
processed in the order listed above. That is, contents will be created before products, which will
be created before subscriptions.

Object creation will be performed with the "create_children" flag set to false by default, as this
could cause data to be overwritten erroneously in cases where our object definitions violate update
conventions (product.derivedProduct is notorious in this regard). This behavior can be flipped by
using the --create_children flag on the CLI.
"""

import argparse
import datetime
import logging
import json
import os
import random
import re
import requests
import string
import sys
import time



# Disable HTTP warnings that'll be kicked out by urllib (used internally by requests)
requests.packages.urllib3.disable_warnings()

# We don't care about urllib/requests logging
logging.getLogger("requests").setLevel(logging.WARNING)
logging.getLogger("urllib3").setLevel(logging.WARNING)

def build_logger(name, msg_format=None):
    """Builds and configures our logger"""
    class EmptyLineFilter(logging.Filter):
        """Logging filter implementation that filters on empty or whitespace-only lines"""
        def __init__(self, invert=False):
            self.invert = invert

        def filter(self, record):
            result = bool(record.msg) and not record.msg.isspace()
            if self.invert:
                result = not result

            return result

    if msg_format is None:
        msg_format = '%(asctime)-15s %(levelname)-7s %(name)s -- %(message)s'

    # Create the base/standard handler
    std_handler = logging.StreamHandler(sys.stdout)
    std_handler.setFormatter(logging.Formatter(fmt=msg_format))
    std_handler.addFilter(EmptyLineFilter())

    # Create an empty-line handler
    empty_handler = logging.StreamHandler(sys.stdout)
    empty_handler.setFormatter(logging.Formatter(fmt=''))
    empty_handler.addFilter(EmptyLineFilter(True))

    # Create a logger using the above handlers
    logger = logging.getLogger(name)
    logger.setLevel(logging.INFO)
    logger.addHandler(std_handler)
    logger.addHandler(empty_handler)

    return logger

log = build_logger(name='generate_manifest')



def is_derived_pool(pool_dto):
    if 'attributes' in pool_dto:
        for attrib in pool_dto['attributes']:
            if attrib['name'] == 'pool_derived' and attrib['value'].lower() == 'true':
                return True

    return False

def ci_in(src_dict, search):
    """Performs a case-insensitive search in a dictionary"""

    search = search.lower()

    for key in src_dict:
        if key.lower() == search:
            return True

    return False

def safe_merge(source, dest, include=None, exclude=None):
    """
    Merges two dictionaries, converting the keys in the source dictionary from snake case to camel case
    where appropriate.
    """

    def handle_match(match):
        return '{chr1}{chr2}'.format(chr1=match.group(1), chr2=match.group(2).upper())

    for key, value in source.items():
        key = re.sub('(\\w)_(\\w)', handle_match, key)

        if (include is None or key in include) and (exclude is None or key not in exclude):
            dest[key] = value

    return dest

class Candlepin:
    """Class providing methods for performing actions against a specific Candlepin server"""
    status_cache = None
    hosted_test = None

    def __init__(self, host='localhost', port=8443, username='admin', password='admin', prefix='candlepin'):
        """
        Creates a new Candlepin object which will connect to the server at the given host/port using the
        specified credentials
        """

        self.host = host
        self.port = port
        self.username = username
        self.password = password
        self.prefix = prefix

    def build_url(self, endpoint):
        """Builds a complete URL for the specified endpoint on the target Candlepin instance"""

        # Remove leading slash if it exists
        if endpoint and endpoint[0] == '/':
            endpoint = endpoint[1:]

        return 'https://{host}:{port}/{prefix}/{endpoint}'.format(
            host=self.host, port=self.port, prefix=self.prefix, endpoint=endpoint)

    def get(self, endpoint, query_params=None, headers=None, content_type='text/plain',
        accept_type='application/json'):
        """Performs a GET request and returns a response object containing the result of the request"""

        return self.request('GET', endpoint, query_params, None, headers, content_type, accept_type)

    def post(self, endpoint, query_params=None, data=None, headers=None, content_type='application/json',
        accept_type='application/json'):
        """Performs a POST request and returns a response object containing the result of the request"""

        return self.request('POST', endpoint, query_params, data, headers, content_type, accept_type)

    def put(self, endpoint, query_params=None, data=None, headers=None, content_type='application/json',
        accept_type='application/json'):
        """Performs a PUT request and returns a response object containing the result of the request"""

        return self.request('PUT', endpoint, query_params, data, headers, content_type, accept_type)

    def delete(self, endpoint, query_params=None, data=None, headers=None, content_type='application/json',
        accept_type='application/json'):
        """Performs a DELETE request and returns a response object containing the result of the request"""

        return self.request('DELETE', endpoint, query_params, data, headers, content_type, accept_type)

    def request(self, req_type, endpoint, query_params=None, data=None, headers=None,
        content_type='application/json', accept_type='application/json'):

        if query_params is None:
            query_params = {}

        if headers is None:
            headers = {}

        # Add our content-type and accept type if they weren't explicitly provided in the headers already
        if not ci_in(headers, 'Content-Type'):
            headers['Content-Type'] = content_type

        if not ci_in(headers, 'Accept'):
            headers['Accept'] = accept_type

        log.debug('Sending request: %s %s (params: %s, headers: %s, data: %s)',
            req_type, endpoint, query_params, headers, data)

        response = requests.request(req_type, self.build_url(endpoint),
            json=data,
            auth=(self.username, self.password),
            params=query_params,
            headers=headers,
            verify=False)

        response.raise_for_status()
        return response

    def status(self):
        if self.status_cache is None:
            self.status_cache = self.get('status').json()

        return self.status_cache

    def has_hosted_test(self):
        if self.hosted_test is None:
            self.hosted_test = not self.status()['standalone']

            if self.hosted_test:
                try:
                    self.get('hostedtest/alive', accept_type='text/plain')
                except requests.exceptions.HTTPError:
                    self.hosted_test = False

        return self.hosted_test


    # Candlepin API functions
    def bind_pool(self, consumer_uuid, pool_id, quantity=1):
        qparams = {
            'pool': pool_id,
            'quantity': quantity
        }

        endpoint = '/consumers/{consumer_uuid}/entitlements'.format(consumer_uuid=consumer_uuid)
        return self.post(endpoint, qparams).json()

    def create_owner(self, owner_data):
        owner = {
            'key': owner_data['key'],
            'displayName': owner_data['displayName']
        }

        if 'contentAccessModeList' in owner_data:
            owner['contentAccessModeList'] = owner_data['contentAccessModeList']

        if 'contentAccessMode' in owner_data:
            owner['contentAccessMode'] = owner_data['contentAccessMode']

        return self.post('owners', {}, owner).json()

    def create_user(self, user_data):
        user = safe_merge(user_data, {}, include=['username', 'password'])
        user['superAdmin'] = user_data['superadmin'] if 'superadmin' in user_data else False

        return self.post('users', {}, user).json()

    def export_manifest(self, consumer_uuid, filename):
        endpoint = "/consumers/{consumer_uuid}/export/async".format(consumer_uuid=consumer_uuid)
        job_status = self.get(endpoint).json()

        # otherwise, wait for the job to complete, then return the manifest
        finished_states = ['ABORTED', 'FINISHED', 'CANCELED', 'FAILED']

        while job_status['state'] not in finished_states:
            time.sleep(1)
            job_status = self.get(job_status['statusPath']).json()

        if job_status['state'] != 'FINISHED':
            raise RuntimeError("manifest export job did not complete successfully: {job}".format(job = job_status))

        export_result = job_status['resultData']

        response = self.get(export_result['href'])

        with open(filename, 'wb+') as file:
            for chunk in response.iter_content(chunk_size=128):
                file.write(chunk)

    def list_owner_pools(self, owner_key):
        # TODO: Add more parameters as needed
        endpoint = '/owners/{owner_key}/pools'.format(owner_key=owner_key)
        return self.get(endpoint).json()

    def refresh_pools(self, owner_key, wait_for_completion=True):
        endpoint = 'owners/{owner_key}/subscriptions'.format(owner_key=owner_key)
        job_status = self.put(endpoint, { 'lazy_regen': True }, None).json()

        if job_status and wait_for_completion:
            finished_states = ['ABORTED', 'FINISHED', 'CANCELED', 'FAILED']

            while job_status['state'] not in finished_states:
                time.sleep(1)
                job_status = self.get(job_status['statusPath']).json()

            if job_status['state'] != 'FINISHED':
                raise RuntimeError("refresh job did not complete successfully: {job}".format(job = job_status))

            # Clean up job status
            # self.delete('jobs', {'id': job_status['id']})

        return job_status

    def register(self, owner_key, type, name, consumer_data = {}):
        consumer_dto = {
            'type': { 'label': type },
            'name': name,
            'facts': {},
            'installedProducts': [],
            'contentTags': [],
            'environments': []
        }

        # Optional fields
        if 'facts' in consumer_data:
            consumer_dto['facts'] = consumer_data['facts']

        if 'installedProducts' in consumer_data:
            consumer_dto['installedProducts'] = consumer_data['installedProducts']

        if 'environments' in consumer_data:
            consumer_dto['environments'] = consumer_data['environments']

        # Send request
        return self.post('consumers', {'owner': owner_key}, consumer_dto).json()


    # Hosted test APIs
    def clear_upstream_data(self):
        return self.delete('hostedtest/')

    def create_upstream_content(self, content_data):
        content_dto = {
            'id': content_data['id'],
            'type': content_data['type'],
            'label': content_data['label'],
            'name': content_data['name'],
            'vendor': content_data['vendor']
        }

        # Optional data
        if 'contentUrl' in content_data:
            content_dto['contentUrl'] = content_data['contentUrl'];

        if 'requiredTags' in content_data:
            content_dto['requiredTags'] = content_data['requiredTags'];

        if 'releaseVer' in content_data:
            content_dto['releaseVer'] = content_data['releaseVer'];

        if 'gpgUrl' in content_data:
            content_dto['gpgUrl'] = content_data['gpgUrl'];

        if 'modifiedProductIds' in content_data:
            content_dto['modifiedProductIds'] = content_data['modifiedProductIds'];

        if 'arches' in content_data:
            content_dto['arches'] = content_data['arches'];

        if 'metadataExpire' in content_data:
            content_dto['metadataExpire'] = content_data['metadataExpire'];

        # Send request
        return self.post('hostedtest/content', {}, content_dto).json()

    def create_upstream_product(self, product_data, create_children=False):
        product_dto = {
            'id': product_data['id'],
            'name': product_data['name'],
        }

        # Add basic optional fields
        if 'multiplier' in product_data:
            product_dto['multiplier'] = product_data['multiplier'];

        if 'dependentProductIds' in product_data:
            product_dto['dependentProductIds'] = product_data['dependentProductIds'];

        if 'derivedProduct' in product_data:
            product_dto['derivedProduct'] = product_data['derivedProduct'];

        if 'providedProducts' in product_data:
            product_dto['providedProducts'] = product_data['providedProducts'];

        if 'branding' in product_data:
            product_dto['branding'] = product_data['branding'];

        # Add attributes, converting dictionaries to lists of awful objects
        if 'attributes' in product_data:
            if isinstance(product_data['attributes'], dict):
                # Convert dictionary to list of objects
                product_dto['attributes'] = [{'name': key, 'value': value} for (key, value) in product_data['attributes'].items()]
            else:
                # Copy it straight up and hope the caller knows what they're doing
                product_dto['attributes'] = product_data['attributes'];

        # Send request
        return self.post('hostedtest/products', {'create_children': create_children}, product_dto).json()

    def map_upstream_content_to_product(self, product_id, contents):
        if isinstance(contents, dict):
            # if contents is a map, use it as-is
            content_map = contents
        elif isinstance(contents, (list, tuple)):
            # if it's listy, convert it to a map
            content_map = { key: "false" for key in contents}
        else:
            # probably a single content id
            content_map = { contents: "false" }

        # Send request
        endpoint = '/products/{product_id}/content'.format(product_id=product_id)
        return self.post(endpoint, {}, content_map).json()

    def create_upstream_subscription(self, subscription_data, create_children=False):
        date_now = datetime.datetime.now()
        start_date = subscription_data['startDate'] if 'startDate' in subscription_data else date_now - datetime.timedelta(days=30)
        end_date = subscription_data['endDate'] if 'endDate' in subscription_data else start_date + datetime.timedelta(days=1825)

        subscription_dto = {
            'id': subscription_data['id'],
            'product': subscription_data['product'],
            'owner': subscription_data['owner'],
            'quantity': subscription_data['quantity'] if 'quantity' in subscription_data else 10,
            'startDate': start_date.isoformat(),
            'endDate': end_date.isoformat(),
        }

        # add in optional data
        if 'contractNumber' in subscription_data:
            subscription_dto['contractNumber'] = subscription_data['contractNumber']

        if 'accountNumber' in subscription_data:
            subscription_dto['accountNumber'] = subscription_data['accountNumber']

        if 'modified' in subscription_data:
            subscription_dto['modified'] = subscription_data['modified']

        if 'lastModified' in subscription_data:
            subscription_dto['lastModified'] = subscription_data['lastModified']

        if 'orderNumber' in subscription_data:
            subscription_dto['orderNumber'] = subscription_data['orderNumber']

        if 'upstreamPoolId' in subscription_data:
            subscription_dto['upstreamPoolId'] = subscription_data['upstreamPoolId']

        if 'upstreamEntitlementId' in subscription_data:
            subscription_dto['upstreamEntitlementId'] = subscription_data['upstreamEntitlementId']

        if 'upstreamConsumerId' in subscription_data:
            subscription_dto['upstreamConsumerId'] = subscription_data['upstreamConsumerId']

        if 'cdn' in subscription_data:
            subscription_dto['cdn'] = subscription_data['cdn']

        # Send request
        return self.post('hostedtest/subscriptions', {'create_children': create_children}, subscription_dto).json()


# Script logic starts here
def parse_options():
    """Parses the arguments provided on the command line"""
    parser = argparse.ArgumentParser(description='Uses a hosted-test enabled Candlepin to generate a manifest file from synthetic data', add_help=True)

    parser.add_argument('--debug', action='store_true', default=False,
        help='Enables debug output')

    parser.add_argument('--username', action='store', default='admin',
        help='The username to use when making requests to Candlepin; defaults to \'admin\'')
    parser.add_argument('--password', action='store', default='admin',
        help='The password to use when making requests to Candlepin; defaults to \'admin\'')
    parser.add_argument('--host', action='store', default='localhost',
        help='The hostname/address of the Candlepin server; defaults to \'localhost\'')
    parser.add_argument('--port', action='store', default=8443, type=int,
        help='The port to use when connecting to Candlepin; defaults to 8443')
    parser.add_argument('--prefix', action='store', default='candlepin',
        help='The endpoint prefix to use on the destination Candlepin server; defaults to \'candlepin\'')

    parser.add_argument('--create_children', action='store_true', default=False,
        help='Automatically create or update children products and content referenced by new subscriptions and products')
    parser.add_argument('--out_file', action='store', default='manifest.zip',
        help='The file name to use when writing the received manifest; defaults to \'manifest.zip\'')
    # parser.add_argument('--stdout', action='store_true', default=False,
    #     help='Output manifest to stdout rather than writing it to a file')

    parser.add_argument("json_files", nargs='*', action='store')

    options = parser.parse_args()

    # Set logging level as appropriate
    if options.debug:
        log.setLevel(logging.DEBUG)

    if len(options.json_files) < 1:
        filename = 'sample_manifest.json'
        log.debug("No test data files provided, defaulting to '%s'", filename)

        if sys.path[0]:
            filename = '{script_home}/{filename}'.format(script_home=sys.path[0], filename=filename)

        options.json_files = [filename]

    return options

def main():
    """Executes the script"""

    try:
        options = parse_options()

        log.info('Connecting to Candlepin @ %s:%d', options.host, options.port)
        cp = Candlepin(options.host, options.port, options.username, options.password, options.prefix)

        if not cp.has_hosted_test():
            log.error("Candlepin does not appear to be running with the hosted-test adapter enabled. Redeploy with the hosted-test adapter and try again.")
            exit(1)

        manifest_data = {
            'contents': [],
            'products': [],
            'subscriptions': []
        }

        for json_file in options.json_files:
            log.info('Loading manifest data from file: %s', json_file)

            with open(json_file) as file:
                data = json.load(file)

                if 'contents' in data and isinstance(data['contents'], (list, tuple)):
                    manifest_data['contents'].extend(data['contents'])

                if 'products' in data and isinstance(data['products'], (list, tuple)):
                    manifest_data['products'].extend(data['products'])

                if 'subscriptions' in data and isinstance(data['subscriptions'], (list, tuple)):
                    manifest_data['subscriptions'].extend(data['subscriptions'])

        if not manifest_data['subscriptions']:
            log.error("Manifest generation requires one or more subscriptions to export")
            exit(1)

        # Randomly generate an org to use for this process
        rnd = int(time.time())
        owner_key = "manifest-{rnd}".format(rnd=rnd)

        owner_dto = {
            'key': owner_key,
            'displayName': owner_key
        }

        log.info("Clearing existing upstream data...")
        cp.clear_upstream_data()

        if len(manifest_data['contents']) > 0:
            log.info('Creating %d upstream content(s)...', len(manifest_data['contents']))

            for content in manifest_data['contents']:
                cp.create_upstream_content(content)

        if len(manifest_data['products']) > 0:
            log.info('Creating %d upstream product(s)...', len(manifest_data['products']))

            for product in manifest_data['products']:
                cp.create_upstream_product(product, options.create_children)

        log.info('Creating %d upstream subscription(s)...', len(manifest_data['subscriptions']))

        for subscription in manifest_data['subscriptions']:
            # forcefully set our owner so we don't pollute some other org space
            subscription['owner'] = owner_dto
            cp.create_upstream_subscription(subscription, options.create_children)

        log.info('Refreshing downstream org...');

        owner_dto = cp.create_owner(owner_dto)
        cp.refresh_pools(owner_key)

        log.info('Building manifest consumer and binding pools...')

        consumer_dto = cp.register(owner_key, 'candlepin', "manifest_consumer-{rnd}".format(rnd=rnd), {
            'facts': {'distributor_version': 'sam-1.3'},
            'releaseVer': '',
            'capabilities': None })

        pool_dtos = cp.list_owner_pools(owner_key)

        for pool in pool_dtos:
            if not is_derived_pool(pool):
                log.info('Binding pool: %s (qty: %d)', pool['productName'], pool['quantity'])
                cp.bind_pool(consumer_dto['uuid'], pool['id'], pool['quantity'])

        log.info("Exporting manifest...")
        filename = options.out_file
        cp.export_manifest(consumer_dto['uuid'], filename)

        log.info('Done! Manifest written to: %s', filename)
    except requests.exceptions.ConnectionError as e:
        log.error('Connection error: %s', e)
        exit(1)


if __name__ == '__main__':
    main()
