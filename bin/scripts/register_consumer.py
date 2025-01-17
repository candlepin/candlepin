#!/usr/bin/env python
from __future__ import print_function, division, absolute_import

"""
Creates consumers using the Candlepin registration endpoint
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

log = build_logger(name='register_consumer')



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

        self.determine_mode()

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

    def determine_mode(self):
        if self.hosted_test is None:
            self.hosted_test = not self.status()['standalone']

            if self.hosted_test:
                try:
                    self.get('hostedtest/alive', accept_type='text/plain')
                except requests.exceptions.HTTPError:
                    self.hosted_test = False

        return self.hosted_test

    # Candlepin API functions
    def register(self, organization, consumer_data):
        qparams = {
            'owner': organization
        }

        headers = {}

        return self.post('consumers', qparams, consumer_data, headers).json()

    def create_owner(self, owner_data):
        owner = {
            'key': owner_data['name'],
            'displayName': owner_data['displayName']
        }

        if 'contentAccessModeList' in owner_data:
            owner['contentAccessModeList'] = owner_data['contentAccessModeList']

        if 'contentAccessMode' in owner_data:
            owner['contentAccessMode'] = owner_data['contentAccessMode']

        owner = self.post('owners', {}, owner).json()

        # If we're operating in hosted-test mode, also create the org upstream for consistency
        if self.hosted_test:
            self.post('hostedtest/owners', {}, { 'key': owner['key'] })

        return owner

    def create_user(self, user_data):
        user = safe_merge(user_data, {}, include=['username', 'password'])
        user['superAdmin'] = user_data['superadmin'] if 'superadmin' in user_data else False

        return self.post('users', {}, user).json()



def parse_options():
    """Parses the arguments provided on the command line"""
    parser = argparse.ArgumentParser(description='Simulates consumer registration via subscription manager', add_help=True)

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

    parser.add_argument('--org', action='store', default='admin',
        help='The organization in which to register the new consumer; defaults to \'admin\'')
    parser.add_argument('--cert_out', action='store', default=None,
        help='The file name to use for writing the consumer\'s identity certificate')
    parser.add_argument('--facts', action='store', default=None,
        help='A JSON object representing additional system facts to include during registration; may override default facts or facts defined by other arguments')
    parser.add_argument('--type', action='store', default='system',
        help='The type of consumer to create; defaults to \'system\'')
    parser.add_argument('--arch', action='store', default='x86_64',
        help='The architecture of consumer to create; defaults to \'x86_64\'')
    parser.add_argument('--sarchs', action='store', default='',
        help='The additional supported architectures for consumer to create; defaults to \'\'')
    parser.add_argument('--no-arch', dest='no_arch', action='store_true',
        help='whether or not the consumer should be created without any architecture information; overrides --arch and --sarchs')

    options = parser.parse_args()

    # Set logging level as appropriate
    if options.debug:
        log.setLevel(logging.DEBUG)

    return options



def main():
    try:
        options = parse_options()

        log.info('Connecting to Candlepin @ %s:%d', options.host, options.port)
        cp = Candlepin(options.host, options.port, options.username, options.password, options.prefix)

        rnd = int(time.time())

        consumer_data = {
            'name': "test_consumer-{rnd}".format(rnd=rnd),

            'capabilities': [
                {"name": "ram"},
                {"name": "cert_v3"}
            ],

            'facts': {
                "uname.machine": options.arch,
                "cpu.cpu_socket(s)": "1",
                "cpu.cpu(s)": "1",
                "system.certificate_version": "3.1",
                "distributor_version": None,
                "virt.is_guest": 'false',
                "supported_architectures": options.sarchs
            },

            'type': {
                'label': options.type
            },

            "serviceLevel": "",
            "environment": None,
            "entitlementStatus": "valid",
            "installedProducts": [],
            "username": "test_user",
            "autoheal": 'false',
            "guestIds": []
        }

        # Clear the facts if we're running no-arch:
        if options.no_arch:
            del consumer_data['facts']['uname.machine']
            del consumer_data['facts']['supported_architectures']

        # Add our CLI options
        if options.facts:
            facts = json.loads(options.facts)
            consumer_data['facts'].update(facts)

        # Register
        log.info("Registering new consumer in org: {org}".format(org=options.org))

        consumer = cp.register(options.org, consumer_data)
        # consumer_json = json.dumps(consumer, indent=4)

        log.info("Consumer created successfully: {uuid}".format(uuid=consumer['uuid']))

        if options.cert_out:
            with open(options.cert_out, "w") as text_file:
                text_file.write(consumer['idCert']['cert'])

            log.info("Identity certificate written to: {cert_file}".format(cert_file=options.cert_out))

    except requests.exceptions.ConnectionError as e:
        log.error('Connection error: %s', e)
        exit(1)

if __name__ == '__main__':
    main()
