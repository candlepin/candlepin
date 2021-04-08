#!/usr/bin/env python
from __future__ import print_function, division, absolute_import

"""
Injects synthetic data from one or more JSON files into Candlepin for testing
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


def build_logger(name, msg_format):
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

# Disable HTTP warnings that'll be kicked out by urllib (used internally by requests)
requests.packages.urllib3.disable_warnings()

# We don't care about urllib/requests logging
logging.getLogger("requests").setLevel(logging.WARNING)
logging.getLogger("urllib3").setLevel(logging.WARNING)

log = build_logger('test_data_importer', '%(asctime)-15s %(levelname)-7s %(name)s -- %(message)s')


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

def random_id(prefix, suffix_length=8):
    """Generates a string with a random suffix, suitable to be used as an ID"""
    suffix = ''.join(random.choice(string.digits) for i in range(suffix_length))
    return '{prefix}-{suffix}'.format(prefix=prefix, suffix=suffix)


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

    def create_role(self, role_name, permissions):
        role = {
            'name': role_name,
            'permissions': permissions
        }

        return self.post('roles', {}, role).json()

    def add_role_to_user(self, username, role_name):
        return self.post('roles/{rolename}/users/{username}'.format(rolename=role_name, username=username)).json()

    def create_content(self, owner_key, content_data):
        content = safe_merge(content_data, {})

        if self.hosted_test:
            url = 'hostedtest/content'
        else:
            url = 'owners/{owner_key}/content'.format(owner_key=owner_key)

        return self.post(url, {}, content).json()

    def create_product(self, owner_key, product_data):
        attributes = product_data['attributes'] if 'attributes' in product_data else {}
        attributes['version'] = product_data['version'] if 'version' in product_data else "1.0"
        attributes['variant'] = product_data['variant'] if 'variant' in product_data else "ALL"
        attributes['arch'] = product_data['arch'] if 'arch' in product_data else "ALL"
        attributes['type'] = product_data['type'] if 'type' in product_data else "SVC"

        branding = []
        content = product_data['content'] if 'content' in product_data else []
        dependent_products = product_data['dependencies'] if 'dependencies' in product_data else []
        relies_on = product_data['relies_on'] if 'relies_on' in product_data else []
        provided_products = []
        derived_product = None

        # correct provided products and derived product
        if 'provided_products' in product_data:
            provided_products = [ { 'id': value } for value in product_data['provided_products'] ]

        if 'derived_product_id' in product_data:
            derived_product = { 'id': product_data['derived_product_id'] }

        # Add branding to some of the products
        # TODO: FIXME: couldn't we just do this in the test data instead?
        if provided_products and 'OS' in product_data['name'] and 'type' in product_data and product_data['type'] == 'MKT':
            branding.append({
                'productId': provided_products[0]['id'],
                'type': 'OS',
                'name': 'Branded {name}'.format(name=product_data['name'])
            })

        product = {
            'id': product_data['id'],
            'name': product_data['name'],
            'multiplier': product_data['multiplier'] if 'multiplier' in product_data else 1,
            'attributes': attributes,
            'dependentProductIds': dependent_products,
            'relies_on': relies_on,
            'branding': branding,
            'providedProducts': provided_products,
            'derivedProduct': derived_product
        }

        # Determine URL and submit
        if self.hosted_test:
            endpoint = 'hostedtest/products'
        else:
            endpoint = 'owners/{owner_key}/products'.format(owner_key=owner_key)

        return self.post(endpoint, {}, product).json()

    def get_product_cert(self, owner_key, product_id):
        endpoint = 'owners/{owner_key}/products/{pid}/certificate'.format(owner_key=owner_key, pid=product_id)
        return self.get(endpoint).json()

    def add_content_to_product(self, owner_key, product_id, content_map):
        if self.hosted_test:
            endpoint = 'hostedtest/products/{pid}/content'.format(pid=product_id)
        else:
            endpoint = 'owners/{owner_key}/products/{pid}/batch_content'.format(owner_key=owner_key, pid=product_id)

        return self.post(endpoint, {}, content_map).json()

    def create_pool(self, owner_key, pool_data):
        start_date = pool_data['start_date'] if 'start_date' in pool_data else datetime.datetime.now()
        end_date = pool_data['end_date'] if 'end_date' in pool_data else start_date + datetime.timedelta(days=365)

        pool = {
            'startDate': start_date,
            'endDate': end_date,
            'quantity': pool_data['quantity'] if 'quantity' in pool_data else 1
        }

        # Merge in the other keys we care about for pools
        keys = ['branding', 'contractNumber', 'accountNumber', 'orderNumber', 'subscriptionId', 'upstreamPoolId']
        safe_merge(pool_data, pool, include=keys)

        if self.hosted_test:
            # Need to set a subscription ID if we're generating this upstream
            pool['id'] = random_id('upstream')

            # Product is an object in hosted test mode
            pool['product'] = { 'id': pool_data['product_id'] }

            # The owner must be set in the subscription data for upstream subscriptions
            pool['owner'] = { 'key': owner_key }

            endpoint = 'hostedtest/subscriptions'
        else:
            # Copy over the pool (special case because hosted test data is different)
            pool['productId'] = pool_data['product_id']

            # Generate fields that make this look like an upstream subscription
            if not 'subscriptionId' in pool:
                pool['subscriptionId'] = random_id('srcsub')

            if 'subscription_subkey' in pool_data:
                pool['subscriptionSubKey'] = pool_data['subscription_subkey']
            elif 'subscriptionSubKey' in pool_data:
                pool['subscriptionSubKey'] = pool_data['subscriptionSubKey']
            else:
                pool['subscriptionSubKey'] = 'master'

            if not 'upstreamPoolId' in pool:
                pool['upstreamPoolId'] = random_id('upstream')

            endpoint = 'owners/{owner_key}/pools'.format(owner_key=owner_key)

        return self.post(endpoint, {}, pool).json()

    def list_pools(self, owner_key):
        endpoint = 'owners/{owner_key}/pools'.format(owner_key=owner_key)
        return self.get(endpoint).json()

    def create_activation_key(self, owner_key, key_data):
        key = safe_merge(key_data, {})
        return self.post('owners/{owner_key}/activation_keys'.format(owner_key=owner_key), {}, key).json()

    def add_pool_to_activation_key(self, key_id, pool_id, quantity=None):
        endpoint = 'activation_keys/{key_id}/pools/{pool_id}'.format(key_id=key_id, pool_id=pool_id)
        params = {}

        if quantity is not None:
            params['quantity'] = quantity

        return self.post(endpoint, params).json()

    def refresh_pools(self, owner_key, wait_for_completion=True):
        endpoint = 'owners/{owner_key}/subscriptions'.format(owner_key=owner_key)
        job_status = self.put(endpoint, { 'lazy_regen': True }, None).json()

        if job_status and wait_for_completion:
            finished_states = ['ABORTED', 'FINISHED', 'CANCELED', 'FAILED']

            while job_status['state'] not in finished_states:
                time.sleep(1)
                job_status = self.get(job_status['statusPath']).json()

            # Clean up job status
            # self.delete('jobs', {'id': job_status['id']})

        return job_status

def create_owners(cp, data):
    """
    Creates any owners present in the test data and returns a dictionary consisting of the owner keys
    mapped to the owner objects returned by Candlepin.
    """

    owners = {}

    if 'owners' in data:
        log.info('')
        log.info('Creating %d owner(s)...', len(data['owners']))

        for owner_data in data['owners']:
            log.info('  Owner: %s  [display name: %s]', owner_data['name'], owner_data['displayName'])

            owner = cp.create_owner(owner_data)
            owners[owner['key']] = owner_data

    return owners

def create_users(cp, data):
    """
    Creates any users present in the test data
    """

    if 'users' in data:
        log.info('')
        log.info('Creating %d user(s)...', len(data['users']))

        for user_data in data['users']:
            log.info('  User: %s  [password: %s, superuser: %s', user_data['username'], user_data['password'],
                user_data['superadmin'] if 'superadmin' in user_data else False)

            user = cp.create_user(user_data)

def create_roles(cp, data):
    """Creates any roles present in the test data, mapping them to users as appropriate"""

    if 'roles' in data:
        log.info('')
        log.info('Creating %d role(s)...', len(data['roles']))

        for role_data in data['roles']:
            permissions = role_data['permissions']
            users = role_data['users']

            log.info('  Role: %s (permissions: %d, users: %d)', role_data['name'], len(permissions),
                len(users))

            # Correct & list permissions
            for perm_data in permissions:
                # convert owner keys to owner objects:
                if type(perm_data['owner']) != dict:
                    perm_data['owner'] = { 'key': perm_data['owner'] }

                log.info('    Permission: [type: %s, owner: %s, access: %s]', perm_data['type'],
                    perm_data['owner']['key'], perm_data['access'])

            role = cp.create_role(role_data['name'], permissions)

            # Add role to users
            for user_data in users:
                log.info('    Applying to user: %s', user_data['username'])
                cp.add_role_to_user(user_data['username'], role_data['name'])

def gather_content(data, hosted_test):
    """Gathers content from the test data and maps it for easy fetching."""

    content = {}

    if 'content' in data:
        content[None] = { element['id']: element for element in data['content'] }

    # If we're operating in hosted mode, everything is global
    if 'owners' in data:
        for owner in data['owners']:
            if 'content' in owner:
                if hosted_test:
                    # FIXME: This potentially breaks if multiple orgs define the same content ID with
                    # different actual content within them. At the time of writing, our standard test
                    # data *doesn't* do this, but we're at risk.
                    content[None].update({ element['id']: element for element in owner['content'] })
                else:
                    content[owner['name']] = { element['id']: element for element in owner['content'] }

    return content

def gather_products(data, hosted_test):
    """
    Gathers products from the test data and orders them in a way to ensure that linear creation of the
    product data is safe
    """
    def order_products(owner_key, product_map):
        ordered = []
        processed_pids = []

        def traverse_product(product):
            if product['id'] in processed_pids:
                return

            if 'derived_product_id' in product:
                if product['derived_product_id'] not in product_map:
                    errmsg = 'product references a non-existent product: {name} ({pid}) => {ref_pid}'.format(
                        name=product['name'], pid=product['id'], ref_pid=product['derived_product_id'])

                    raise ReferenceError(errmsg)

                traverse_product(product_map[product['derived_product_id']])

            if 'provided_products' in product:
                for pid in product['provided_products']:
                    if pid not in product_map:
                        errmsg = 'product references a non-existent product: {name} ({pid}) => {ref_pid}'.format(
                            name=product['name'], pid=product['id'], ref_pid=pid)

                        raise ReferenceError(ermsg)

                    traverse_product(product_map[pid])

            processed_pids.append(product['id'])
            ordered.append((owner_key, product))

        for product in product_map.values():
            traverse_product(product)

        return ordered

    products = []

    if hosted_test:
        product_map = {}

        # In hosted mode, we don't need per-org products, so we only add the global products once
        if 'products' in data:
            for product in data['products']:
                product_map[product['id']] = product

        if 'owners' in data:
            for owner in data['owners']:
                if 'products' in owner:
                    for product in owner['products']:
                        product_map[product['id']] = product

        products.extend(order_products(None, product_map))
    else:
        # In standalone mode, we can only add stuff if we have an org to add it to
        if 'owners' in data:
            for owner in data['owners']:
                owner_key = owner['name']
                product_map = {}

                if 'products' in data:
                    for product in data['products']:
                        product_map[product['id']] = product

                if 'products' in owner:
                    for product in owner['products']:
                        product_map[product['id']] = product

                products.extend(order_products(owner_key, product_map))

    return products

def map_content_to_product(cp, owner_key, product_data, content_data):
    """Performs content generation and mapping for a given product"""

    # TODO: FIXME: Redo the test_data.json format so content is defined as part of the product
    # rather than screwing around with faking unique content and mapping it to each individual
    # product. Content sharing is eventually going to be removed from Candlepin, and sharing
    # a single content instance between multiple products breaks repo generation/management very
    # badly.
    def find_content(content_id):
        if owner_key in content_data:
            if content_id in content_data[owner_key]:
                return content_data[owner_key][content_id]

        global_content = content_data[None]

        if content_id in global_content:
            return global_content[content_id]

        errmsg = 'product references a non-existent content: {name} ({pid}) => {ref_cid}'.format(
            name=product_data['name'], pid=product_data['id'], ref_cid=content_id)

        raise ReferenceError(errmsg)


    content_map = {}
    product_content = []

    if 'content' in product_data:
        # Test data stores this as an array of arrays for "reasons." Convert it to a dictionary and pass it
        # to Candlepin
        for content_id, enabled in product_data['content']:
            # Create a unique content specifically for this product-content pairing.
            content = find_content(content_id).copy()
            content_id = '{pid}-{cid}'.format(pid=product_data['id'], cid=content_id)

            # Modify the base content data so that it is unique for this product
            content['id'] = content_id
            content['name'] = '{name}-{uid}'.format(name=content['name'], uid=product_data['id'])
            content['label'] = '{label}-{uid}'.format(label=content['label'], uid=product_data['id'])
            content['content_url'] = '{url}/{uid}'.format(url=content['content_url'], uid=content_id)

            product_content.append(content)
            content_map[content_id] = enabled

    if product_content:
        for content_data in product_content:
            log.info('    Content: %s [id: %s, url: %s]', content_data['name'], content_data['id'],
                content_data['content_url'])
            cp.create_content(owner_key, content_data)

    if content_map:
        cp.add_content_to_product(owner_key, product_data['id'], content_map)

def create_products(cp, data):
    """Creates any products present in the test data and maps any content associated with them"""

    log.info('')
    product_data = gather_products(data, cp.hosted_test)
    content_data = gather_content(data, cp.hosted_test)

    log.info('Creating %d product(s)...', len(product_data))
    for owner_key, product in product_data:
        log.info('  Product: %s [id: %s, owner: %s]', product['name'], product['id'],
            owner_key if owner_key else '-GLOBAL-')

        cp.create_product(owner_key, product)

        # Link products to any content they reference
        map_content_to_product(cp, owner_key, product, content_data)

def fetch_product_certs(cp, cert_dir, engineering_products):
    """
    Fetches product certs for the specified engineering products. Engineering products must be provided as
    a dictionary mapping a product ID to an owner key of one of the orgs owning the product.
    """

    if engineering_products:
        log.info('')
        log.info('Fetching certs for %d engineering products...', len(engineering_products))

        # make sure the cert directory exists
        if not os.path.exists(cert_dir):
            os.makedirs(cert_dir)

        for pid, owner_key in engineering_products.items():
            try:
                # This could 404 if the product did not get pulled down during refresh (hosted mode)
                # because it's not linked/used by anything. We'll issue a warning in this case.
                product_cert = cp.get_product_cert(owner_key, pid)
                cert_filename = '{dir}/{prod_id}.pem'.format(dir=cert_dir, prod_id=pid)

                with open(cert_filename, 'w+') as file:
                    log.info('  Writing certificate: %s', cert_filename)
                    file.write(product_cert['cert'])
            except requests.exceptions.HTTPError as e:
                if e.response.status_code == 404:
                    log.warn('  Skipping certificate for unused product: %s', pid)
                else:
                    raise e

def create_subscriptions(cp, options, owners, data):
    """
    Creates subscriptions from the test data and fetches engineering product certificates,
    refreshing the affected orgs as necessary.
    """

    log.info('')
    log.info("Creating subscriptions...")

    now = datetime.datetime.now()
    engineering_products = {}

    for owner_key, owner_data in owners.items():
        account_number = ''.join(random.choice(string.digits) for i in range(10))
        order_number = random_id('order')

        products = []
        pools = []

        if 'products' in data:
            products.extend(data['products'])

        if 'products' in owner_data:
            products.extend(owner_data['products'])

        for product in products:
            if 'type' in product and product['type'] == 'MKT':
                if 'quantity' in product:
                    small_quantity = int(product['quantity'])
                    large_quantity = small_quantity * 10 if small_quantity > 0 else 10
                else:
                    small_quantity = 5
                    large_quantity = 50

                start_date = (now + datetime.timedelta(days=-5)).isoformat()
                end_date = (now + datetime.timedelta(days=360)).isoformat()

                # Small quantity pool
                pools.append({
                    'product_id': product['id'],
                    'quantity': small_quantity,
                    'contract_number': 0,
                    'account_number': account_number,
                    'order_number': order_number,
                    'start_date': start_date,
                    'end_date': end_date
                })

                # Large quantity pool
                pools.append({
                    'product_id': product['id'],
                    'quantity': large_quantity,
                    'contract_number': 0,
                    'account_number': account_number,
                    'order_number': order_number,
                    'start_date': start_date,
                    'end_date': end_date
                })

                # Future pool
                pools.append({
                    'product_id': product['id'],
                    'quantity': large_quantity,
                    'contract_number': 0,
                    'account_number': account_number,
                    'order_number': order_number,
                    'start_date': (now + datetime.timedelta(days=30)).isoformat(),
                    'end_date': end_date
                })

            else:
                # Product is an engineering product. Flag the pid/owner combo so we can fetch the cert later

                # Impl note:
                # We're abusing some CP knowledge here in that product certs are global -- it doesn't matter
                # *which* org we use to fetch them, but as the API requires *an* org, we just store the last
                # org that specifies the product which should still cover the entire collection of products
                # generated during this execution
                engineering_products[product['id']] = owner_key

        log.info('  Creating %d subscription(s) for owner: %s', len(pools), owner_key)

        for pool in pools:
            cp.create_pool(owner_key, pool)

        # Refresh (if necessary)
        if cp.hosted_test:
            log.info('  Refreshing owner: %s', owner_key)
            cp.refresh_pools(owner_key)

    # Fetch engineering product certs (if necessary)
    fetch_product_certs(cp, options.cert_dir, engineering_products)

def create_activation_keys(cp, owners):
    """
    Creates activation keys for the specified owners. The owners should be provided as a dictionary
    consisting of owner keys mapped to owner data
    """
    activation_keys = []

    # Build a set of activation key data to use to generate our keys
    for owner_key in owners:
        activation_keys.append((owner_key, "default_key", None))
        activation_keys.append((owner_key, "awesome_os_pool", None))

        pools = cp.list_pools(owner_key)

        for pool in pools:
            key_name = '{owner_key}-{prod_id}-key-{pool_id}'.format(
                owner_key=owner_key, prod_id=pool['productId'], pool_id=pool['id'])

            activation_keys.append((owner_key, key_name, pool))

    # Actually generate the keys (if necessary)
    if activation_keys:
        log.info('')
        log.info('Creating %d activation keys...', len(activation_keys))

        for owner_key, key_name, pool in activation_keys:
            log.info('  Activation key: %s [owner: %s, pool: %s]', key_name, owner_key,
                pool['id'] if pool is not None else 'n/a')

            key = cp.create_activation_key(owner_key, {'name': key_name})
            if pool is not None:
                cp.add_pool_to_activation_key(key['id'], pool['id'])


def process_file(cp, options, filename):
    """Process a given JSON file, creating the test data defined within"""
    with open(filename) as file:
        data = json.load(file)

    log.info('Importing data from file: %s', filename)

    # Create owners
    owners = create_owners(cp, data)

    # Create users
    create_users(cp, data)

    # Create roles
    create_roles(cp, data)

    # Create products
    create_products(cp, data)

    # Create pools/subscriptions
    create_subscriptions(cp, options, owners, data)

    # Create activation keys
    create_activation_keys(cp, owners)

    # Done!
    log.info('')
    log.info('Finished importing data from file: %s', filename)

def parse_options():
    """Parses the arguments provided on the command line"""
    parser = argparse.ArgumentParser(description='Imports JSON-formatted test data to a Candlepin server', add_help=True)

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

    parser.add_argument('--cert_dir', action='store', default='generated_certs',
        help='The directory in which to store generated product certificates; defaults to \'generated_certs\'')

    parser.add_argument("json_files", nargs='*', action='store')

    options = parser.parse_args()

    # Set logging level as appropriate
    if options.debug:
        log.setLevel(logging.DEBUG)

    if len(options.json_files) < 1:
        log.debug("No test data files provided, defaulting to 'test_data.json'")

        filename = 'test_data.json'
        if sys.path[0]:
            filename = '{script_home}/{filename}'.format(script_home=sys.path[0], filename=filename)

        options.json_files = [filename]

    return options

def main():
    """Executes the script"""

    try:
        log.info('Importing test data...')
        options = parse_options()

        log.info('Connecting to Candlepin @ %s:%d', options.host, options.port)
        cp = Candlepin(options.host, options.port, options.username, options.password, options.prefix)

        log.info('Importing test data from %d file(s)...', len(options.json_files))
        for file in options.json_files:
            process_file(cp, options, file)

        log.info('Complete. Files imported: %d', len(options.json_files))

    except requests.exceptions.ConnectionError as e:
        log.error('Connection error: %s', e)
        exit(1)


if __name__ == '__main__':
    main()
