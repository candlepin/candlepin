#!/usr/bin/env python

# The CP connector provides various means for connecting to Candlepin or its supported backing databases.
# This file should be imported and used by other scripts, rather than having business logic added to it
# directly.

# This requires mysql-connector or psycopg2 for mysql/mariadb and postgresql respectively. "Compatible"
# packages may introduce odd issues (i.e. mysql-connector-python is known to cause problems)

import os

import logging

logging.basicConfig(level=logging.INFO, format="%(asctime)-15s %(levelname)-7s %(name)-16s %(message)s")
log = logging.getLogger('cp_connector')



class HTTPConnector(object):
    def __init__(self):
        pass



class DBConnector(object):
    class TransactionContext():
        def __init__(self, db):
            self._active = True
            self._db = db

        def __enter__(self):
            # Since we return this from a start_transaction call, we don't need to do anything
            # here
            return self

        def __exit__(self, exc_type, exc_value, traceback):
            # If we still have a transaction open, we need to commit on successful exit and
            # rollback on exception
            if self._active and self._db.in_transaction():
                if exc_type is None:
                    self.commit()
                else:
                    log.debug("with-statement exited with exception; rolling back transaction")
                    self.rollback()

            # Do not suppress any exceptions
            return False

        def commit(self):
            if not self._active or not self._db.in_transaction():
                raise RuntimeError("Transaction already closed")

            self._db.commit()
            self._active = False

        def rollback(self):
            if not self._active or not self._db.in_transaction():
                raise RuntimeError("Transaction already closed")

            self._db.rollback()
            self._active = False



    def __init__(self, db):
        self.db = db

        # Default auto-commit to True to make some of the transaction stuff easier to setup later
        # This will be automatically shut off once a transaction is started.
        self.db.autocommit = True

    def __del__(self):
        self.close()

    def backend(self):
        raise NotImplementedError("Not yet implemented")

    def get_type_as_string(self, type_code):
        raise NotImplementedError("Not yet implemented")

    def build_insert_statement(self, table, columns, ignore_duplicates=False):
        raise NotImplementedError("Not yet implemented")

    def close(self):
        if not self.is_closed():
            self.db.close()

    def is_closed(self):
        raise NotImplementedError("Not yet implemented")

    def start_transaction(self, readonly=False):
        raise NotImplementedError("Not yet implemented")

    def in_transaction(self):
        raise NotImplementedError("Not yet implemented")

    def commit(self):
        log.debug("Committing transaction")
        self.db.commit()
        self.db.autocommit=True

    def rollback(self):
        log.debug("Rolling back transaction")
        self.db.rollback()
        self.db.autocommit=True

    def cursor(self):
        return self.db.cursor()

    def execQuery(self, query, parameters=()):
        cursor = None

        try:
            cursor = self.cursor()
            cursor.execute(query, parameters)

            return cursor
        except Exception as error:
            if cursor is not None:
                cursor.close()

            raise error

    def execUpdate(self, query, parameters=()):
        cursor = None

        try:
            cursor = self.cursor()

            cursor.execute(query, parameters)
            count = cursor.rowcount

            cursor.close()

            return count
        except Exception as error:
            self.rollback()

            if cursor is not None:
                cursor.close()

            raise error



class PSQLConnector(DBConnector):
    def __init__(self, host, port, username, password, dbname):
        import psycopg2 as psql
        self.psql = psql

        params = {}
        params['host'] = host

        # Port does not use default when None is passed in; skip it if it's not provided
        if port is not None:
            params['port'] = port

        params['user'] = username
        params['password'] = password
        params['dbname'] = dbname

        super(PSQLConnector, self).__init__(psql.connect(**params))

        # Get the types from the DB so we can map them for storing types
        self._init_types()

    def backend(self):
        return 'PostgreSQL'

    def _init_types(self):
        cursor = self.execQuery('SELECT oid, typname FROM pg_type')

        self._type_map = {}
        for row in cursor:
            self._type_map[row[0]] = row[1]

        cursor.close()

    def get_type_as_string(self, type_code):
        if type_code in self._type_map:
            return self._type_map[type_code]

        return None

    def build_insert_statement(self, table, columns, ignore_duplicates=False):
        pblock = ', '.join(['%s'] * len(columns))
        statement = 'INSERT INTO ' + table + ' (' + ', '.join(columns) + ') VALUES (' + pblock + ')'

        if ignore_duplicates:
            statement = statement + ' ON CONFLICT DO NOTHING'

        return statement

    def is_closed(self):
        if hasattr(self, 'db') and self.db is not None and self.db.closed == 0:
            return False

        return True

    def start_transaction(self, readonly=False):
        self.db.set_session(readonly=readonly, autocommit=False)

        # Transactions are automatically started on the first command if autocommit is set to false, so
        # let's do a quick no-op query to get things started
        cursor = self.cursor()
        cursor.execute('SELECT 1')
        cursor.close()

        return DBConnector.TransactionContext(self)


    def in_transaction(self):
        tx_states = [self.psql.extensions.STATUS_BEGIN, self.psql.extensions.STATUS_IN_TRANSACTION, self.psql.extensions.STATUS_PREPARED]
        return not self.is_closed() and self.db.status in tx_states



class MySQLConnector(DBConnector):
    def __init__(self, host, port, username, password, dbname):
        import mysql.connector as mysql
        self.mysql = mysql

        class DataConverter(mysql.conversion.MySQLConverter):
            DATE_TYPES = (mysql.FieldType.DATE, mysql.FieldType.DATETIME)

            def row_to_python(self, row, fields):
                row = super(DataConverter, self).row_to_python(row, fields)

                def convert(type, value):
                    if value is not None:
                        if type in self.DATE_TYPES:
                            return str(value)

                    return value

                return [convert(field[1], col) for (col, field) in zip(row, fields)]

        params = {}
        params['host'] = host

        # Port does not use default when None is passed in; skip it if it's not provided
        if port is not None:
            params['port'] = port

        params['user'] = username
        params['password'] = password
        params['database'] = dbname

        # Add our converter class
        params['converter_class'] = DataConverter

        super(MySQLConnector, self).__init__(mysql.connect(**params))

    def backend(self):
        return 'MySQL/MariaDB'

    def get_type_as_string(self, type_code):
        from mysql.connector import FieldType
        return FieldType.get_info(type_code)

    def build_insert_statement(self, table, columns, ignore_duplicates=False):
        pblock = ', '.join(['%s'] * len(columns))
        statement = ' INTO ' + table + ' (' + ', '.join(columns) + ') VALUES (' + pblock + ')'

        if ignore_duplicates:
            statement = 'INSERT IGNORE' + statement
        else:
            statement = 'INSERT' + statement

        return statement

    def is_closed(self):
        if hasattr(self, 'db') and self.db is not None and self.db.is_connected():
            return False

        return True

    def start_transaction(self, readonly=False):
        # Impl note: Read-only isn't supported. We could hack it by making commit do a rollback instead,
        # but that feels worse than doing nothing. Eventually remove this and start supporting it again.
        log.debug("MySql read-only transactions are not supported; ignoring config");

        self.db.autocommit=False
        self.db.start_transaction()
        return DBConnector.TransactionContext(self)

    def in_transaction(self):
        return not self.is_closed() and self.db.in_transaction



def set_log_level(level):
    log.setLevel(level)

def get_http_connector(host, username, password, secure):
    pass #TODO

def get_db_connector(type, host='localhost', port=None, username='candlepin', password='', dbname='candlepin'):
    connectors = {
        'psql':         PSQLConnector,
        'postgre':      PSQLConnector,
        'postgres':     PSQLConnector,
        'postgresql':   PSQLConnector,

        'mariadb':      MySQLConnector,
        'mysql':        MySQLConnector
    }

    return connectors[str(type).lower()](host, port, username, password, dbname)
