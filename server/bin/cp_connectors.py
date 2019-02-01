#!/usr/bin/env python

# The CP connector provides various means for connecting to Candlepin or its supported backing databases.
# This file should be imported and used by other scripts, rather than having business logic added to it
# directly.

import os

import logging

logging.basicConfig(level=logging.INFO, format="%(asctime)-15s %(levelname)-7s %(name)-16s %(message)s")
log = logging.getLogger('cp_connector')



class HTTPConnector(object):
    def __init__(self):
        pass



class DBConnector(object):
    def __init__(self, db):
        self.db = db

    def __del__(self):
        self.close()

    def backend(self):
        raise NotImplementedError("Not yet implemented")

    def close(self):
        if not self.is_closed():
            self.db.close()

    def is_closed(self):
        raise NotImplementedError("Not yet implemented")

    def commit(self):
        self.db.commit()

    def rollback(self):
        self.db.rollback()

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

            self.commit()
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

    def backend(self):
        return 'PostgreSQL'

    def is_closed(self):
        if self.db is not None and self.db.closed == 0:
            return False

        return True



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

    def is_closed(self):
        if self.db is not None and self.db.is_connected():
            return False

        return True





def get_http_connector(host, username, password, secure):
    pass #TODO

def get_db_connector(type, host='localhost', port=None, username='candlepin', password='', dbname='candlepin'):
    connectors = {
        'postgre':      PSQLConnector,
        'postgres':     PSQLConnector,
        'postgresql':   PSQLConnector,
        'mariadb':      MySQLConnector,
        'mysql':        MySQLConnector
    }

    return connectors[str(type).lower()](host, port, username, password, dbname)

