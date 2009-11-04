import sqlalchemy
from sqlalchemy import create_engine
from sqlalchemy import Table, Column, Integer, String, MetaData, ForeignKey
from sqlalchemy.orm import mapper
from sqlalchemy.orm import sessionmaker

#engine = create_engine('sqlite:///:memory:', echo=True)
engine = create_engine('postgres://candlepin:candlepin@127.0.0.1:5432/candlepin')

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

product_definition_table = Table('cp_product', metadata, 
	Column('uuid', String, primary_key=True),
	Column('name', String))

entitlement_pool_table = Table('cp_entitlement_pool', metadata, 
	Column('uuid', String, primary_key=True),
	Column('name', String),
	Column('owner', String, ForeignKey('cp_owner.uuid')),
	Column('product', String, ForeignKey('cp_product.uuid')))

metadata.create_all(engine)
