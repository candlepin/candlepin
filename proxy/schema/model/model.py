import sqlalchemy
from sqlalchemy import create_engine
from sqlalchemy import Table, Column, Integer, String, MetaData, ForeignKey
from sqlalchemy.orm import mapper
from sqlalchemy.orm import sessionmaker

# engine = create_engine('sqlite:///:memory:', echo=True)
engine = create_engine('postgres://mmccune:mmccune@127.0.0.1:5432/candlepin')

metadata = MetaData()

org_table = Table('org', metadata, 
	Column('uuid', String, primary_key=True),
	Column('name', String))

entitlement_table = Table('entitlement', metadata, 
	Column('uuid', String, primary_key=True),
	Column('name', String),
	Column('org', String, ForeignKey('org.uuid')),
	Column('parent', String, ForeignKey('entitlement.uuid')))

consumer_table = Table('consumer', metadata, 
	Column('uuid', String, primary_key=True),
	Column('name', String),
	Column('type', String),
	Column('org', String, ForeignKey('org.uuid')),
	Column('parent', String, ForeignKey('consumer.uuid')))

product_definition_table = Table('product_definition', metadata, 
	Column('uuid', String, primary_key=True),
	Column('name', String))

entitlement_pool_table = Table('entitlement_pool', metadata, 
	Column('uuid', String, primary_key=True),
	Column('name', String),
	Column('org', String, ForeignKey('org.uuid')),
	Column('product_definition', String, ForeignKey('product_definition.uuid')))

metadata.create_all(engine)


class Org(object):
	def __init__(self, uuid, name):
		self.uuid = uuid
		self.name = name

	def __repr__(self):
		return "<Org('%s','%s')>" % (self.uuid, self.name)

mapper(Org, org_table) 

Session = sessionmaker(bind=engine)
session = Session()

org1 = Org('289489090234', 'test org')
session.add(org1)

lookedup = session.query(Org).filter_by(name='test org').first()
print lookedup.uuid
