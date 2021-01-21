#!/usr/bin/env python

import binascii
import datetime
from functools import partial
import json
import logging
from optparse import OptionParser
import os
import re
import zipfile

import cp_connectors as cp

logging.basicConfig(level=logging.INFO, format="%(asctime)-15s %(levelname)-7s %(name)-16s %(message)s")
log = logging.getLogger('org_migrator')

LOGLVL_TRACE = 5
logging.addLevelName(LOGLVL_TRACE, 'TRACE')



def get_cursor_columns(db, cursor):
    if hasattr(cursor, 'column_names'):
        return cursor.column_names
    elif hasattr(cursor, 'description'):
        return [col.name for col in cursor.description]
    else:
        raise Exception('Cannot determine column names for cursor')


def get_cursor_column_types(db, cursor):
    if hasattr(cursor, 'column_names'):
        return [db.get_type_as_string(col[1]) for col in cursor.description]
    elif hasattr(cursor, 'description'):
        return [db.get_type_as_string(col.type_code) for col in cursor.description]
    else:
        raise Exception('Cannot determine column types for cursor')


def resolve_org(db, org):
    org_id = None

    cursor = db.execQuery("SELECT id FROM cp_owner WHERE account=%s", (org,))
    row = cursor.fetchone()

    if row is not None:
        org_id = row[0]

    cursor.close()

    return org_id


def list_orgs(db):
    orgs = []

    cursor = db.execQuery("SELECT DISTINCT account FROM cp_owner");
    for row in cursor:
        orgs.append(row[0])

    return orgs


def jsonify(data):
    def data_converter(obj):
        if isinstance(obj, (bytearray, buffer, memoryview)):
            return binascii.b2a_base64(obj)
        if isinstance(obj, datetime.datetime):
            return str(obj)

        raise TypeError("Unexpected type: %s" % (type(obj)))

    return json.dumps(data, default=data_converter)


dbobj_re = re.compile('[A-Za-z0-9_]+')
def validate_column_names(table, columns):
    if dbobj_re.match(table) is None:
        raise Exception("Table %s uses invalid characters" % (table,))

    for column in columns:
        if dbobj_re.match(column) is None:
            raise Exception("Column %s.%s uses invalid characters" % (table, column))


def boolean_converter(columns, chain=None):
    def convert_row(col_names, row):
        for col in columns:
            idx = col_names.index(col)
            row[idx] = bool(row[idx])

        if callable(chain):
            row = chain(col_names, row)

        return row
    return convert_row


def base64_decoder(columns, chain=None):
    def decode_row(col_names, row):
        for col in columns:
            idx = col_names.index(col)
            row[idx] = binascii.a2b_base64(row[idx])

        if callable(chain):
            row = chain(row)

        return row
    return decode_row



class ModelManager(object):
    def __init__(self, org_id, archive, db, ignore_dupes):
        self.org_id = org_id
        self.db = db
        self.archive = archive
        self.ignore_dupes = ignore_dupes

        self._imported = False
        self._exported = False

    def do_export(self):
        raise NotImplementedError("Not yet implemented")

    def do_import(self):
        raise NotImplementedError("Not yet implemented")

    def depends_on(self):
        return []

    @property
    def exported(self):
        return self._exported

    @property
    def imported(self):
        return self._imported

    def _write_cursor_to_json(self, file, table, cursor, row_cb=None):
        output = {
            'table':        table,
            'columns':      get_cursor_columns(self.db, cursor),
            'column_types': get_cursor_column_types(self.db, cursor),
            'rows':         []
        }

        for row in cursor:
            if callable(row_cb):
                row = row_cb(row)

            output['rows'].append(row)

        self.archive.writestr(file, jsonify(output))
        log.debug('Exported %d rows for table: %s', len(output['rows']), table)

    def _export_query(self, file, table, query, params=()):
        cursor = self.db.execQuery(query, params)
        self._write_cursor_to_json(file, table, cursor)
        cursor.close()

    def _bulk_insert(self, table, columns, rows, row_hook=None):
        validate_column_names(table, columns)

        log.debug('Importing %d rows into table: %s', len(rows), table)
        statement = self.db.build_insert_statement(table, columns, self.ignore_dupes)

        # try:
        cursor = self.db.cursor()

        if len(rows) > 0:
            log.log(LOGLVL_TRACE, 'Inserting using statement: %s', statement)
        else:
            log.log(LOGLVL_TRACE, 'No rows to insert; skipping insert')

        for row in rows:
            if callable(row_hook):
                row = row_hook(columns, row)

            log.log(LOGLVL_TRACE, '  row: %s', row)
            cursor.execute(statement, row)

        cursor.close()

        return True
        # TODO: Uncomment this if we figure out a sane way to get Python to not hide the original
        # stack trace
        # except Exception as e:
        #     self.db.rollback()
        #     raise e

    def _import_json(self, file, row_hook=None):
        log.debug('Importing data from file: %s', file)
        result = False

        with self.archive.open(file) as fp:
            data = json.load(fp)

            if data.get('table') is None:
                raise Exception("Malformed table name in json file: %s => %s" % (file, data.get('table')))

            if type(data.get('columns')) != list or len(data.get('columns')) < 0:
                raise Exception("Malformed column list in JSON file: %s => %s" % (file, data.get('columns')))

            if type(data.get('rows')) == list:
                if len(data.get('rows')) > 0:
                    result = self._bulk_insert(data['table'], data['columns'], data['rows'], row_hook)
                else:
                    result = True

        if not result:
            log.error('Unable to import JSON from file: %s', file)

        return result



class OwnerManager(ModelManager):
    def __init__(self, org_id, archive, db, ignore_dupes):
        super(OwnerManager, self).__init__(org_id, archive, db, ignore_dupes)

    def do_export(self):
        if self.exported:
            return True

        self._export_query('cp_owner.json', 'cp_owner', 'SELECT * FROM cp_owner WHERE id=%s', (self.org_id,))

        self._exported = True
        return True

    def do_import(self):
        if self.imported:
            return True

        result = self._import_json('cp_owner.json')

        self._imported = result
        return result



class UeberCertManager(ModelManager):
    def __init__(self, org_id, archive, db, ignore_dupes):
        super(UeberCertManager, self).__init__(org_id, archive, db, ignore_dupes)

    def depends_on(self):
        return [OwnerManager]

    def do_export(self):
        if self.exported:
            return True

        self._export_query('cp_cert_serial-ueber.json', 'cp_cert_serial', 'SELECT cs.* FROM cp_cert_serial cs JOIN cp_ueber_cert uc ON uc.serial_id = cs.id WHERE uc.owner_id=%s', (self.org_id,))
        self._export_query('cp_ueber_cert.json', 'cp_ueber_cert', 'SELECT * FROM cp_ueber_cert WHERE owner_id=%s', (self.org_id,))

        self._exported = True
        return True

    def do_import(self):
        if self.imported:
            return True

        result = self._import_json('cp_cert_serial-ueber.json')
        result = result and self._import_json('cp_ueber_cert.json', base64_decoder(['cert', 'privatekey']))

        # Impl note:
        # cert tables that reference their parent object are imported by those managers

        self._imported = result
        return result



class ContentManager(ModelManager):
    def __init__(self, org_id, archive, db, ignore_dupes):
        super(ContentManager, self).__init__(org_id, archive, db, ignore_dupes)

    def depends_on(self):
        return [OwnerManager]

    def do_export(self):
        if self.exported:
            return True

        content_query = 'SELECT DISTINCT * FROM ' + \
            '(SELECT c.* FROM cp2_content c ' + \
            'JOIN cp2_owner_content oc ON oc.content_uuid = c.uuid AND oc.owner_id = %s ' + \
            'UNION ' + \
            'SELECT c.* FROM cp2_content c ' + \
            'JOIN cp2_product_content pc ON pc.content_uuid = c.uuid  ' + \
            'JOIN cp2_owner_products op ON op.product_uuid = pc.product_uuid AND op.owner_id = %s ' + \
            'UNION ' + \
            'SELECT c.* FROM cp2_content c ' + \
            'JOIN cp2_environment_content ec ON ec.content_uuid = c.uuid ' + \
            'JOIN cp_environment e ON ec.environment_id = e.id AND e.owner_id = %s) AS content'

        cmp_query = 'SELECT DISTINCT * FROM ' + \
            '(SELECT cmp.* FROM cp2_content_modified_products cmp ' + \
            'JOIN cp2_owner_content oc ON oc.content_uuid = cmp.content_uuid AND oc.owner_id = %s ' + \
            'UNION ' + \
            'SELECT cmp.* FROM cp2_content_modified_products cmp ' + \
            'JOIN cp2_product_content pc ON pc.content_uuid = cmp.content_uuid  ' + \
            'JOIN cp2_owner_products op ON op.product_uuid = pc.product_uuid AND op.owner_id = %s ' + \
            'UNION ' + \
            'SELECT cmp.* FROM cp2_content_modified_products cmp ' + \
            'JOIN cp2_environment_content ec ON ec.content_uuid = cmp.content_uuid ' + \
            'JOIN cp_environment e ON ec.environment_id = e.id AND e.owner_id = %s) AS content'

        self._export_query('cp2_content.json', 'cp2_content', content_query, (self.org_id, self.org_id, self.org_id,))
        self._export_query('cp2_content_modified_products.json', 'cp2_content_modified_products', cmp_query, (self.org_id, self.org_id, self.org_id,))
        self._export_query('cp2_owner_content.json', 'cp2_owner_content', 'SELECT * FROM cp2_owner_content WHERE owner_id=%s', (self.org_id,))

        self._exported = True
        return True

    def do_import(self):
        if self.imported:
            return True

        result = self._import_json('cp2_content.json')
        result = result and self._import_json('cp2_owner_content.json')
        result = result and self._import_json('cp2_content_modified_products.json')

        self._imported = result
        return result



class ProductManager(ModelManager):
    def __init__(self, org_id, archive, db, ignore_dupes):
        super(ProductManager, self).__init__(org_id, archive, db, ignore_dupes)

    def depends_on(self):
        return [OwnerManager, ContentManager]

    def do_export(self):
        if self.exported:
            return True

        self._export_query('cp2_products.json', 'cp2_products', 'SELECT p.* FROM cp2_products p JOIN cp2_owner_products op ON op.product_uuid = p.uuid WHERE op.owner_id = %s', (self.org_id,))
        self._export_query('cp2_product_attributes.json', 'cp2_product_attributes', 'SELECT pa.* FROM cp2_product_attributes pa JOIN cp2_owner_products op ON op.product_uuid = pa.product_uuid WHERE op.owner_id = %s', (self.org_id,))
        self._export_query('cp2_product_certificates.json', 'cp2_product_certificates', 'SELECT pc.* FROM cp2_product_certificates pc JOIN cp2_owner_products op ON op.product_uuid = pc.product_uuid WHERE op.owner_id = %s', (self.org_id,))
        self._export_query('cp2_product_content.json', 'cp2_product_content', 'SELECT pc.* FROM cp2_product_content pc JOIN cp2_owner_products op ON op.product_uuid = pc.product_uuid WHERE op.owner_id = %s', (self.org_id,))
        self._export_query('cp2_owner_products.json', 'cp2_owner_products', 'SELECT * FROM cp2_owner_products WHERE owner_id=%s', (self.org_id,))
        self._export_query('cp2_product_branding.json', 'cp2_product_branding', 'SELECT pb.* FROM cp2_product_branding pb JOIN cp2_owner_products op ON op.product_uuid = pb.product_uuid WHERE op.owner_id = %s', (self.org_id,))
        self._export_query('cp2_product_provided_products.json', 'cp2_product_provided_products', 'SELECT ppp.* FROM cp2_product_provided_products ppp JOIN cp2_owner_products op ON op.product_uuid = ppp.product_uuid WHERE op.owner_id = %s', (self.org_id,))

        self._exported = True
        return True

    def do_import(self):
        if self.imported:
            return True

        result = self._import_json('cp2_products.json')
        result = result and self._import_json('cp2_product_attributes.json')
        result = result and self._import_json('cp2_product_certificates.json', base64_decoder(['cert', 'privatekey']))
        result = result and self._import_json('cp2_product_content.json', boolean_converter(['enabled']))
        result = result and self._import_json('cp2_owner_products.json')
        result = result and self._import_json('cp2_product_branding.json')
        result = result and self._import_json('cp2_product_provided_products.json')

        self._imported = result
        return result



class EnvironmentManager(ModelManager):
    def __init__(self, org_id, archive, db, ignore_dupes):
        super(EnvironmentManager, self).__init__(org_id, archive, db, ignore_dupes)

    def depends_on(self):
        return [OwnerManager, ContentManager]

    def do_export(self):
        if self.exported:
            return True

        self._export_query('cp_environment.json', 'cp_environment', 'SELECT * FROM cp_environment WHERE owner_id=%s', (self.org_id,))
        self._export_query('cp2_environment_content.json', 'cp2_environment_content', 'SELECT ec.* FROM cp2_environment_content ec JOIN cp_environment e ON ec.environment_id = e.id WHERE e.owner_id=%s', (self.org_id,))
        self._export_query('cp_owner_env_content_access.json', 'cp_owner_env_content_access', 'SELECT eca.* FROM cp_owner_env_content_access eca JOIN cp_environment e ON eca.environment_id = e.id WHERE e.owner_id=%s', (self.org_id,))

        self._exported = True
        return True

    def do_import(self):
        if self.imported:
            return True

        result = self._import_json('cp_environment.json')
        result = result and self._import_json('cp2_environment_content.json')
        result = result and self._import_json('cp_owner_env_content_access.json')

        self._imported = result
        return result



class ConsumerManager(ModelManager):
    def __init__(self, org_id, archive, db, ignore_dupes):
        super(ConsumerManager, self).__init__(org_id, archive, db, ignore_dupes)

    def depends_on(self):
        return [OwnerManager, ContentManager, EnvironmentManager]

    def do_export(self):
        if self.exported:
            return True

        # Consumer certificate stuff
        self._export_query('cp_cert_serial-cac.json', 'cp_cert_serial', 'SELECT cs.* FROM cp_cert_serial cs JOIN cp_cont_access_cert cac ON cac.serial_id = cs.id JOIN cp_consumer c ON c.cont_acc_cert_id = cac.id WHERE c.owner_id=%s', (self.org_id,))
        self._export_query('cp_cont_access_cert.json', 'cp_cont_access_cert', 'SELECT cac.* FROM cp_cont_access_cert cac JOIN cp_consumer c ON c.cont_acc_cert_id = cac.id WHERE c.owner_id=%s', (self.org_id,))

        self._export_query('cp_cert_serial-ic.json', 'cp_cert_serial', 'SELECT cs.* FROM cp_cert_serial cs JOIN cp_id_cert ic ON ic.serial_id = cs.id JOIN cp_consumer c ON c.consumer_idcert_id = ic.id WHERE c.owner_id=%s', (self.org_id,))
        self._export_query('cp_id_cert-local.json', 'cp_id_cert', 'SELECT ic.* FROM cp_id_cert ic JOIN cp_consumer c ON c.consumer_idcert_id = ic.id WHERE c.owner_id=%s', (self.org_id,))

        self._export_query('cp_cert_serial-uc.json', 'cp_cert_serial', 'SELECT cs.* FROM cp_cert_serial cs JOIN cp_id_cert ic ON ic.serial_id = cs.id JOIN cp_upstream_consumer uc ON uc.consumer_idcert_id = ic.id WHERE uc.owner_id=%s', (self.org_id,))
        self._export_query('cp_id_cert-upstream.json', 'cp_id_cert', 'SELECT ic.* FROM cp_id_cert ic JOIN cp_upstream_consumer uc ON uc.consumer_idcert_id = ic.id WHERE uc.owner_id=%s', (self.org_id,))

        self._export_query('cp_key_pair.json', 'cp_key_pair', 'SELECT ckp.* FROM cp_key_pair ckp JOIN cp_consumer c ON c.keypair_id = ckp.id WHERE c.owner_id=%s', (self.org_id,))

        # Consumer
        self._export_query('cp_consumer_type.json', 'cp_consumer_type', 'SELECT * FROM cp_consumer_type', [])
        self._export_query('cp_consumer.json', 'cp_consumer', 'SELECT * FROM cp_consumer WHERE owner_id=%s', (self.org_id,))
        self._export_query('cp_consumer_capability.json', 'cp_consumer_capability', 'SELECT cc.* FROM cp_consumer_capability cc JOIN cp_consumer c ON c.id = cc.consumer_id WHERE c.owner_id=%s', (self.org_id,))
        self._export_query('cp_consumer_content_tags.json', 'cp_consumer_content_tags', 'SELECT cct.* FROM cp_consumer_content_tags cct JOIN cp_consumer c ON c.id = cct.consumer_id WHERE c.owner_id=%s', (self.org_id,))
        self._export_query('cp_consumer_facts.json', 'cp_consumer_facts', 'SELECT cf.* FROM cp_consumer_facts cf JOIN cp_consumer c ON c.id = cf.cp_consumer_id WHERE c.owner_id=%s', (self.org_id,))
        self._export_query('cp_consumer_guests.json', 'cp_consumer_guests', 'SELECT cg.* FROM cp_consumer_guests cg JOIN cp_consumer c ON c.id = cg.consumer_id WHERE c.owner_id=%s', (self.org_id,))
        self._export_query('cp_consumer_guests_attributes.json', 'cp_consumer_guests_attributes', 'SELECT cga.* FROM cp_consumer_guests_attributes cga JOIN cp_consumer_guests cg ON cg.guest_id = cga.cp_consumer_guest_id JOIN cp_consumer c ON c.id = cg.consumer_id WHERE c.owner_id=%s', (self.org_id,))
        self._export_query('cp_consumer_hypervisor.json', 'cp_consumer_hypervisor', 'SELECT ch.* FROM cp_consumer_hypervisor ch JOIN cp_consumer c ON c.id = ch.consumer_id WHERE c.owner_id=%s', (self.org_id,))
        self._export_query('cp_installed_products.json', 'cp_installed_products', 'SELECT ip.* FROM cp_installed_products ip JOIN cp_consumer c ON c.id = ip.consumer_id WHERE c.owner_id=%s', (self.org_id,))
        self._export_query('cp_content_override.json', 'cp_content_override', 'SELECT co.* FROM cp_content_override co JOIN cp_consumer c ON c.id = co.consumer_id WHERE c.owner_id=%s', (self.org_id,))
        self._export_query('cp_sp_add_on.json', 'cp_sp_add_on', 'SELECT spa.* FROM cp_sp_add_on spa JOIN cp_consumer c ON c.id = spa.consumer_id WHERE c.owner_id=%s', (self.org_id,))

        # Misc consumer stuff
        self._export_query('cp_upstream_consumer.json', 'cp_upstream_consumer', 'SELECT * FROM cp_upstream_consumer WHERE owner_id=%s', (self.org_id,))
        self._export_query('cp_deleted_consumers.json', 'cp_deleted_consumers', 'SELECT * FROM cp_deleted_consumers WHERE owner_id=%s', (self.org_id,))

        self._exported = True
        return True

    def do_import(self):
        if self.imported:
            return True

        result = self._import_consumer_types('cp_consumer_type.json')

        result = result and self._import_json('cp_cert_serial-cac.json', boolean_converter(['collected', 'revoked']))
        result = result and self._import_json('cp_cont_access_cert.json', base64_decoder(['cert', 'privatekey']))
        result = result and self._import_json('cp_cert_serial-ic.json', boolean_converter(['collected', 'revoked']))
        result = result and self._import_json('cp_id_cert-local.json', base64_decoder(['cert', 'privatekey']))
        result = result and self._import_json('cp_cert_serial-uc.json', boolean_converter(['collected', 'revoked']))
        result = result and self._import_json('cp_id_cert-upstream.json', base64_decoder(['cert', 'privatekey']))
        result = result and self._import_json('cp_key_pair.json', base64_decoder(['publickey', 'privatekey']))

        result = result and self._import_json('cp_consumer.json', boolean_converter(['autoheal']))
        result = result and self._import_json('cp_consumer_capability.json')
        result = result and self._import_json('cp_consumer_content_tags.json')
        result = result and self._import_json('cp_consumer_facts.json')
        result = result and self._import_json('cp_consumer_guests.json')
        result = result and self._import_json('cp_consumer_guests_attributes.json')
        result = result and self._import_json('cp_consumer_hypervisor.json')
        result = result and self._import_json('cp_installed_products.json')
        result = result and self._import_json('cp_content_override.json')
        result = result and self._import_json('cp_sp_add_on.json')

        result = result and self._import_json('cp_upstream_consumer.json')
        result = result and self._import_json('cp_deleted_consumers.json')

        self._imported = result
        return result

    def _import_consumer_types(self, file):
        log.debug('Importing consumer types from file: %s', file)
        result = False

        with self.archive.open(file) as fp:
            data = json.load(fp)

            if data.get('table') is None:
                raise Exception("Malformed table name in json file: %s => %s" % (file, data.get('table')))

            if type(data.get('columns')) != list or len(data.get('columns')) < 0:
                raise Exception("Malformed column list in JSON file: %s => %s" % (file, data.get('columns')))

            if type(data.get('rows')) == list:
                if len(data.get('rows')) > 0:
                    result = self._insert_types(data['table'], data['columns'], data['rows'])
                else:
                    result = True

        if not result:
            log.error('Unable to import consumer types from file: %s', file)

        return result

    def _insert_types(self, table, columns, rows):
        log.debug('Importing %d rows into table: %s', len(rows), table)
        pblock = ', '.join(['%s'] * len(columns))

        validate_column_names(table, columns)
        insert_stmt = 'INSERT INTO ' + table + ' (' + ', '.join(columns) + ') VALUES (' + pblock + ')'
        update_stmt = 'UPDATE ' + table + ' SET ' + (', '.join((col + '=%s' for col in columns))) + ' WHERE id=%s'

        label_idx = None

        for (idx, col) in enumerate(columns):
            if col == 'label':
                label_idx = idx
                break

        if label_idx is None:
            raise Exception("Consumer types lacks a label column")

        inserted = 0
        updated = 0


        # TODO: optimize this so we can do like 10-25 rows at a time, rather than just one
        try:
            for row in rows:
                # impl note:
                # This sucks, but at the time of writing, there is no platform-agnostic upsert statement
                label = row[label_idx]
                cursor = self.db.execQuery('SELECT id FROM ' + table + ' WHERE label=%s', (label,))

                erow = cursor.fetchone()
                cursor.close()

                if erow is not None:
                    # Existing row, update it with the data we have
                    rowcount = self.db.execUpdate(update_stmt, row + [erow[0]])
                    updated = updated + rowcount
                else:
                    # New consumer type, insert row as-is
                    rowcount = self.db.execUpdate(insert_stmt, row)
                    inserted = inserted + rowcount

            log.debug("Consumer types committed, %d updated, %d inserted", updated, inserted)
            return True
        except Exception as e:
            self.db.rollback()
            raise e



class PoolManager(ModelManager):
    def __init__(self, org_id, archive, db, ignore_dupes):
        super(PoolManager, self).__init__(org_id, archive, db, ignore_dupes)

    def depends_on(self):
        return [OwnerManager, ProductManager, ConsumerManager]

    def do_export(self):
        if self.exported:
            return True

        # CDN
        self._export_query('cp_cert_serial-cdn.json', 'cp_cert_serial', 'SELECT cs.* FROM cp_cert_serial cs JOIN cp_cdn_certificate cc ON cc.serial_id = cs.id JOIN cp_cdn c ON c.certificate_id = cc.id JOIN cp_pool p ON p.cdn_id = c.id WHERE p.owner_id=%s', (self.org_id,))
        self._export_query('cp_cdn_certificate.json', 'cp_cdn_certificate', 'SELECT cc.* FROM cp_cdn_certificate cc JOIN cp_cdn c ON c.certificate_id = cc.id JOIN cp_pool p ON p.cdn_id = c.id WHERE p.owner_id=%s', (self.org_id,))
        self._export_query('cp_cdn.json', 'cp_cdn', 'SELECT c.* FROM cp_cdn c JOIN cp_pool p ON p.cdn_id = c.id WHERE p.owner_id=%s', (self.org_id,))

        # Pool certs
        self._export_query('cp_cert_serial-pool.json', 'cp_cert_serial', 'SELECT cs.* FROM cp_cert_serial cs JOIN cp_certificate c ON c.serial_id = cs.id JOIN cp_pool p ON p.certificate_id = c.id WHERE p.owner_id=%s', (self.org_id,))
        self._export_query('cp_certificate.json', 'cp_certificate', 'SELECT c.* FROM cp_certificate c JOIN cp_pool p ON p.certificate_id = c.id WHERE p.owner_id=%s', (self.org_id,))

        # Recursive pool/entitlement lookup!

        # 1 find pools for the org that have no source entitlement (sourceentitlement_id is null)
        # 2 find entitlements for the pools
        # 3 find pools for the org which originate from the entitlements found
        # 4 if one or more pools is found in step 3, go to step 2

        param_limit = 10000

        def id_puller(list, col, row):
            list.append(row[col])
            return row

        def fetch_pools(eids, depth=0):
            if len(eids) < 1:
                return

            pids = []
            rows = []
            columns = None

            while len(eids) > 0:
                block = eids[:param_limit]
                eids = eids[param_limit:]

                pblock = ', '.join(['%s'] * len(block))
                cursor = self.db.execQuery('SELECT e.* FROM cp_pool p WHERE p.sourceentitlement_id IN (' + pblock + ') ORDER BY created ASC', block)
                columns = get_cursor_columns(self.db, cursor)
                column_types = get_cursor_column_types(self.db, cursor)
                rows = rows + list(cursor)
                cursor.close()

            output = {
                'table':        'cp_pool',
                'columns':      columns,
                'column_types': column_types,
                'rows':         rows
            }

            self.archive.writestr("cp_pool-%d.json" % (depth,), jsonify(output))
            fetch_entitlements(pids, depth)

        def fetch_entitlements(pids, depth=0):
            if len(pids) < 1:
                return

            eids = []
            rows = []
            columns = None

            while len(pids) > 0:
                block = pids[:param_limit]
                pids = pids[param_limit:]

                pblock = ', '.join(['%s'] * len(block))
                cursor = self.db.execQuery('SELECT e.* FROM cp_entitlement e WHERE e.pool_id IN (' + pblock + ') ORDER BY created ASC', block)
                columns = get_cursor_columns(self.db, cursor)
                column_types = get_cursor_column_types(self.db, cursor)
                rows = rows + list(cursor)
                cursor.close()

            output = {
                'table':        'cp_entitlement',
                'columns':      columns,
                'column_types': column_types,
                'rows':         rows
            }

            self.archive.writestr("cp_entitlement-%d.json" % (depth,), jsonify(output))
            fetch_pools(eids, depth + 1)

        # Fetch base pools (those not originating from source entitlements)
        pids = []

        cursor = self.db.execQuery('SELECT p.* FROM cp_pool p WHERE owner_id=%s AND sourceentitlement_id is NULL ORDER BY created ASC', (self.org_id,))
        self._write_cursor_to_json('cp_pool-0.json', 'cp_pool', cursor, partial(id_puller, pids, 0))
        cursor.close()

        fetch_entitlements(pids)

        # From here, we can blanket export any related pool data for pools in this org, since we're
        # no longer worried about the circular referencing between pool and entitlement

        self._export_query('cp_pool_attribute.json', 'cp_pool_attribute', 'SELECT pa.* FROM cp_pool_attribute pa JOIN cp_pool p ON p.id = pa.pool_id WHERE p.owner_id=%s', (self.org_id,))
        self._export_query('cp_pool_source_stack.json', 'cp_pool_source_stack', 'SELECT pss.* FROM cp_pool_source_stack pss JOIN cp_pool p ON p.id = pss.derivedpool_id WHERE p.owner_id=%s', (self.org_id,))
        self._export_query('cp2_pool_source_sub.json', 'cp2_pool_source_sub', 'SELECT pss.* FROM cp2_pool_source_sub pss JOIN cp_pool p ON p.id = pss.pool_id WHERE p.owner_id=%s', (self.org_id,))
        self._export_query('cp_product_pool_attribute.json', 'cp_product_pool_attribute', 'SELECT ppa.* FROM cp_product_pool_attribute ppa JOIN cp_pool p ON p.id = ppa.pool_id WHERE p.owner_id=%s', (self.org_id,))

        # Entitlement certs (note: unlike all other certs, these must be inserted *after* entitlements)
        self._export_query('cp_cert_serial-ent.json', 'cp_cert_serial', 'SELECT cs.* FROM cp_cert_serial cs JOIN cp_ent_certificate ec ON ec.serial_id = cs.id JOIN cp_entitlement e ON e.id = ec.entitlement_id JOIN cp_pool p ON p.id = e.pool_id WHERE p.owner_id=%s', (self.org_id,))
        self._export_query('cp_ent_certificate.json', 'cp_ent_certificate', 'SELECT ec.* FROM cp_ent_certificate ec JOIN cp_entitlement e ON e.id = ec.entitlement_id JOIN cp_pool p ON p.id = e.pool_id WHERE p.owner_id=%s', (self.org_id,))

        self._exported = True
        return True

    def do_import(self):
        if self.imported:
            return True

        result = self._import_json('cp_cert_serial-cdn.json', boolean_converter(['collected', 'revoked']))
        result = result and self._import_json('cp_cdn_certificate.json', base64_decoder(['cert', 'privatekey']))
        result = result and self._import_json('cp_cdn.json')
        result = result and self._import_json('cp_cert_serial-pool.json', boolean_converter(['collected', 'revoked']))
        result = result and self._import_json('cp_certificate.json', base64_decoder(['cert', 'privatekey']))

        try:
            # do iterative pool/entitlement import until we hit a key error, indicating we're out of
            # files to import
            depth = 0
            while True:
                result = result and self._import_json("cp_pool-%d.json" % (depth,), boolean_converter(['activesubscription']))
                result = result and self._import_json("cp_entitlement-%d.json" % (depth,), boolean_converter(['dirty', 'updatedonstart']))

                depth = depth + 1
        except KeyError:
            pass

        result = result and self._import_json('cp_pool_attribute.json')
        result = result and self._import_json('cp_pool_source_stack.json')
        result = result and self._import_json('cp2_pool_source_sub.json')
        result = result and self._import_json('cp_product_pool_attribute.json')
        result = result and self._import_json('cp_cert_serial-ent.json', boolean_converter(['collected', 'revoked']))
        result = result and self._import_json('cp_ent_certificate.json', base64_decoder(['cert', 'privatekey']))

        self._imported = result
        return result



class ActivationKeyManager(ModelManager):
    def __init__(self, org_id, archive, db, ignore_dupes):
        super(ActivationKeyManager, self).__init__(org_id, archive, db, ignore_dupes)

    def depends_on(self):
        return [OwnerManager, ProductManager, PoolManager]

    def do_export(self):
        if self.exported:
            return True

        self._export_query('cp_activation_key.json', 'cp_activation_key', 'SELECT * FROM cp_activation_key WHERE owner_id=%s', (self.org_id,))
        self._export_query('cp_activationkey_pool.json', 'cp_activationkey_pool', 'SELECT akp.* FROM cp_activationkey_pool akp JOIN cp_activation_key ak ON ak.id = akp.key_id WHERE ak.owner_id=%s', (self.org_id,))
        self._export_query('cp2_activation_key_products.json', 'cp2_activation_key_products', 'SELECT akp.* FROM cp2_activation_key_products akp JOIN cp2_owner_products op ON op.product_uuid = akp.product_uuid WHERE op.owner_id=%s', (self.org_id,))

        self._exported = True
        return True

    def do_import(self):
        if self.imported:
            return True

        result = self._import_json('cp_activation_key.json')
        result = result and self._import_json('cp_activationkey_pool.json')
        result = result and self._import_json('cp2_activation_key_products.json')

        self._imported = result
        return result



class OrgMigrator(object):
    workers = [OwnerManager, ProductManager, ContentManager, EnvironmentManager, ConsumerManager, PoolManager, UeberCertManager, ActivationKeyManager]

    def __init__(self, dbconn, archive, org_id, ignore_dupes):
        self.db = dbconn
        self.archive = archive
        self.org_id = org_id
        self.ignore_dupes = ignore_dupes

        self.exporters = {}

    def __del__(self):
        self.close()

    def close(self):
        if self.archive is not None:
            self.archive.close()

    def _get_model_exporter(self, exporter):
        if exporter not in self.exporters:
            self.exporters[exporter] = exporter(self.org_id, self.archive, self.db, self.ignore_dupes)

        return self.exporters[exporter]

    def execute(self):
        raise NotImplementedError("Not yet implemented")



class OrgExporter(OrgMigrator):
    def __init__(self, dbconn, archive_file, org_id, ignore_dupes):
        archive = zipfile.ZipFile(archive_file, mode='w', compression=zipfile.ZIP_DEFLATED)

        super(OrgExporter, self).__init__(dbconn, archive, org_id, ignore_dupes)

    def execute(self):
        # Impl note:
        # Order doesn't matter for export, so we can just run through the list once
        for task in self.workers:
            exporter = self._get_model_exporter(task)

            if not exporter.exported:
                log.info('Beginning export task: %s', task.__name__)

                if not exporter.do_export():
                    log.error('Export task unsuccessful: %s', task.__name__)
                    return False

                log.info('Export task completed: %s', task.__name__)
            else:
                log.debug('Skipping redundant export task: %s', task.__name__)

        return True



class OrgImporter(OrgMigrator):
    def __init__(self, dbconn, archive_file, org_id, ignore_dupes):
        archive = zipfile.ZipFile(archive_file, 'r')

        super(OrgImporter, self).__init__(dbconn, archive, org_id, ignore_dupes)

    def execute(self):
        return self._import_impl(self.workers)

    def _import_impl(self, task_list, depth=0):
        if depth > 100:
            raise Exception("Dependency graph exceeded 100 levels on task: %s" % (task.__name__,))

        for task in task_list:
            importer = self._get_model_exporter(task)

            dependent_tasks = importer.depends_on()

            # recursively handle dependencies...
            if not self._import_impl(dependent_tasks, depth + 1):
                return False

            if not importer.imported:
                log.info('Beginning import task: %s', task.__name__)

                if not importer.do_import():
                    log.error('Import task unsuccessful: %s', task.__name__)
                    return False

                log.info('Import task completed: %s', task.__name__)
            else:
                log.debug('Skipping redundant import task: %s', task.__name__)

        return True



def parse_options():
    usage = "usage: %prog ORG"
    parser = OptionParser(usage=usage)

    parser.add_option("--debug", action="store_true", default=False,
        help="Enables debug output")
    parser.add_option("--trace", action="store_true", default=False,
        help="Enables trace output; implies --debug")

    parser.add_option("--dbtype", action="store", default='postgresql',
        help="The type of database to target. Can target MySQL, MariaDB or PostgreSQL; defaults to PostgreSQL")
    parser.add_option("--username", action="store", default='candlepin',
        help="The username to use when connecting to the database; defaults to 'candlepin'")
    parser.add_option("--password", action="store", default='',
        help="The password to use when connecting to the database")
    parser.add_option("--host", action="store", default='localhost',
        help="The hostname/address of the database server; defaults to 'localhost'")
    parser.add_option("--port", action="store", default=None,
        help="The port to use when connecting to the database server")
    parser.add_option("--db", action="store", default='candlepin',
        help="The database to use; defaults to 'candlepin'")
    parser.add_option("--file", action="store", default='export.zip',
        help="The name of the file to export to or import from; defaults to 'export.zip'")

    parser.add_option("--import", dest='act_import', action="store_true", default=False,
        help="Sets the operating mode to IMPORT; cannot be used with --export or --list")
    parser.add_option("--export", dest='act_export', action="store_true", default=False,
        help="Sets the operating mode to EXPORT; cannot be used with --import or --list")
    parser.add_option("--list", dest="act_list", action="store_true", default=False,
        help="Sets the operating mode to LIST; cannot be used with --import or --export")

    parser.add_option("--ignore_dupes", dest="ignore_dupes", action="store_true", default=False,
        help="Ignores duplicate entities during import")

    (options, args) = parser.parse_args()

    if not options.act_import and not options.act_export and not options.act_list:
        parser.error("One of --import, --export, or --list must be specified")

    if not (options.act_import ^ options.act_export ^ options.act_list) or (options.act_import and options.act_export and options.act_list):
        parser.error("Only one of --import, --export, and --list may be specified in a given command")

    if len(args) < 1 and options.act_export:
        parser.error("Must provide an organization to export")

    return (options, args)



def main():
    (options, args) = parse_options()

    if options.debug:
        log.setLevel(logging.DEBUG)
        cp.set_log_level(logging.DEBUG)

    if options.trace:
        log.setLevel(LOGLVL_TRACE)
        cp.set_log_level(LOGLVL_TRACE)

    db = None

    try:
        db = cp.get_db_connector(options.dbtype, options.host, options.port, options.username, options.password, options.db)
        log.info('Using database: %s @ %s', db.backend(), options.host)

        if options.act_import:
            # Use the provided argument if the --file is still default
            if options.file == 'export.zip' and len(args) > 0:
                options.file = args[0]

            # Verify the file exists (as a file) and can be read so we can kick out a cleaner
            # error message than a spooky exception
            if not os.access(options.file, os.R_OK) or not os.path.isfile(options.file):
                log.error("File does not exist or cannot be read: %s", options.file)
                return

            importer = OrgImporter(db, options.file, None, options.ignore_dupes)

            log.info('Importing data from file: %s', options.file)
            with(db.start_transaction(readonly=False)) as transaction:
                if importer.execute():
                    transaction.commit()
                    log.info('Task complete! Shutting down...')
                else:
                    transaction.rollback()
                    log.error("Import task failed. Shutting down...")

        elif options.act_export:
            org_id = resolve_org(db, args[0])

            if org_id is not None:
                log.info('Resolved org "%s" to org ID: %s', args[0], org_id)

                exporter = OrgExporter(db, options.file, org_id, False)

                log.info('Exporting data to file: %s', options.file)
                with(db.start_transaction(readonly=True)) as transaction:
                    if exporter.execute():
                        transaction.commit()
                        log.info('Task complete! Shutting down...')
                    else:
                        transaction.rollback()
                        log.error("Import task failed. Shutting down...")
            else:
                log.error("No such org: %s", args[0])

        elif options.act_list:
            print("Available orgs: %s" % (list_orgs(db)))

    finally:
        if db is not None:
            db.close()


if __name__ == "__main__":
    main()
