import sqlalchemy
from sqlalchemy import create_engine
from sqlalchemy import Table, Column, Integer, String, MetaData, ForeignKey, Sequence
from sqlalchemy.orm import mapper
from sqlalchemy.orm import sessionmaker
from optparse import OptionParser

import sys

metadata = MetaData()

owner_table = Table('cp_owner', metadata, 
	Column('uuid', String, primary_key=True),
	Column('name', String))

entitlement_table = Table('cp_entitlement', metadata, 
	Column('uuid', String, primary_key=True),
	Column('name', String),
	Column('owner', String, ForeignKey('cp_owner.uuid')),
	Column('parent', String, ForeignKey('cp_entitlement.uuid')))

consumer_table = Table('cp_consumer', metadata, 
	Column('uuid', String, primary_key=True),
	Column('name', String),
	Column('owner', String, ForeignKey('cp_owner.uuid')),
	Column('parent', String, ForeignKey('cp_consumer.uuid')))

consumer_info_to_consumer_table = Table('cp_consumer_info_to_consumer', metadata,
	Column('cid', String, ForeignKey('cp_consumer.uuid')),
	Column('info_id', Integer, ForeignKey('cp_consumer_info.id')))

consumer_type_table = Table('cp_consumer_type', metadata,
	Column('id', Integer, Sequence('consumer_type_id_seq'), primary_key=True),
	Column('label', String))

consumer_info_table = Table('cp_consumer_info', metadata,
	Column('id', Integer, Sequence('consumer_info_id_seq'), primary_key=True),
	Column('name', String),
	Column('value', String),
	Column('type', Integer, ForeignKey('cp_consumer_type.id')))

product_definition_table = Table('cp_product', metadata, 
	Column('uuid', String, primary_key=True),
	Column('name', String))

entitlement_pool_table = Table('cp_entitlement_pool', metadata, 
	Column('uuid', String, primary_key=True),
	Column('name', String),
	Column('owner', String, ForeignKey('cp_owner.uuid')),
	Column('product', String, ForeignKey('cp_product.uuid')))

def get_engine(db):
    if db == "postgresql":
        return create_engine('postgres://candlepin:candlepin@127.0.0.1:5432/candlepin')
    elif db == "hsqldb":
        return
    elif db == "sqlite":
        return create_engine('sqlite:///:memory:', echo=True)

def create_db(db):
    engine = get_engine(db)
    metadata.create_all(engine)

def drop_db(db):
    engine = get_engine(db)
    metadata.drop_all(engine)

if __name__ == "__main__":
    usage = "%prog [options]"
    description = "desc"
    parser = OptionParser(usage=usage, description=description)
    parser.add_option("--drop", dest="drop", action="store_true",
        default=False, help="drops all tables")
    parser.add_option("--create", dest="create", action="store_true",
        default=False, help="creates all tables")

    parser.add_option("--postgres", dest="db", action="store_const",
        const="postgresql", default=False, help="use PostgreSQL engine")
    parser.add_option("--hsqldb", dest="db", action="store_const",
        const="hsqldb", default=False, help="use HSQLDB engine")
    parser.add_option("--sqlite", dest="db", action="store_const",
        const="sqlite", default=False, help="use SQLite engine")

    (options, args) = parser.parse_args()


    # validate options
    if not options.db:
        parser.print_help()
        sys.exit(1)

    if not (options.drop or options.create):
        parser.print_help()
        sys.exit(1)

    if options.create:
        create_db(options.db)
    elif options.drop:
        drop_db(options.db)
