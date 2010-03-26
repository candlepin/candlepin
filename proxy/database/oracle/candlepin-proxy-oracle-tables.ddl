create table cp_attribute (
	id number(19,0) not null, 
	name varchar2(255 char) not null, 
	value varchar2(255 char), 
	primary key (id));

create table cp_attribute_hierarchy (
	PARENT_ATTRIBUTE_ID number(19,0) not null, 
	CHILD_ATTRIBUTE_ID number(19,0) not null, 
	primary key (PARENT_ATTRIBUTE_ID, 
	CHILD_ATTRIBUTE_ID), unique (CHILD_ATTRIBUTE_ID));

create table cp_certificate (
	id number(19,0) not null, 
	certificate_blob clob, 
	owner_id number(19,0) not null, 
	primary key (id));

create table cp_consumer (
	id number(19,0) not null, 
	name varchar2(255 char) not null, 
	uuid varchar2(255 char) not null unique,
        consumer_idcert_id number(19,0), 
	consumer_fact_id number(19,0), 
	owner_id number(19,0) not null, 
	parent_consumer_id number(19,0), 
	type_id number(19,0) not null, 
	primary key (id));

create table cp_consumer_ent_certificate (
	id number(19,0) not null, 
	key BLOB not null, 
	pem BLOB not null, 
	serialNumber number(19,2) not null, 
	entitlement_id number(19,0) not null, 
	primary key (id));

create table cp_consumer_entitlements (
	consumer_id number(19,0), 
	entitlement_id number(19,0) not null, 
	primary key (entitlement_id));

create table cp_consumer_facts (
	cp_consumer_id number(19,0) not null, 
	element varchar2(255 char), 
	mapkey varchar2(255 char), 
	primary key (cp_consumer_id, mapkey));

create table cp_consumer_facts_metadata (
	cp_consumer_facts_id number(19,0) not null, 
	element varchar2(255 char), 
	mapkey varchar2(255 char), 
	primary key (cp_consumer_facts_id, mapkey));
	
create table cp_id_cert (
       id number(19,0) not null, 
       cert BLOB not null, 
       key BLOB not null, 
       serial number(19,2) not null, 
       primary key (id));

create table cp_consumer_type (
	id number(19,0) not null, 
	label varchar2(255 char) not null unique, 
	primary key (id));

create table cp_entitlement (
	id number(19,0) not null, 
	isFree number(1,0), 
	startDate timestamp, 
	owner_id number(19,0) not null, 
	pool_id number(19,0) not null, 
	primary key (id));

create table cp_entitlement_pool_attribute (
	cp_pool_id number(19,0) not null, 
	attributes_id number(19,0) not null, 
	primary key (cp_pool_id, attributes_id), 
	unique (attributes_id));

create table cp_owner (
	id number(19,0) not null, 
	name varchar2(255 char) not null unique, 
	primary key (id));

create table cp_pool (
	id number(19,0) not null, 
	activeSubscription number(1,0), 
	consumed number(19,0) not null, 
	endDate timestamp not null, 
	productId varchar2(255 char), 
	quantity number(19,0) not null, 
	startDate timestamp not null, 
	subscriptionId number(19,0), 
	owner_id number(19,0) not null, 
	sourceEntitlement_id number(19,0), 
	primary key (id));

create table cp_product (
	id varchar2(255 char) not null, 
	label varchar2(255 char) not null unique, 
	name varchar2(255 char) not null unique, 
	primary key (id));

create table cp_product_attribute (
	cp_product_id varchar2(255 char) not null, 
	attributes_id number(19,0) not null, 
	primary key (cp_product_id, attributes_id), 
	unique (attributes_id));

create table cp_product_hierarchy (
	parent_product_id varchar2(255 char) not null, 
	child_product_id varchar2(255 char) not null, 
	primary key (parent_product_id, 
	child_product_id), 
	unique (child_product_id));

create table cp_rules (
	id number(19,0) not null, 
	rules_blob clob, 
	primary key (id));

create table cp_subscription (
	id number(19,0) not null, 
	endDate timestamp not null, 
	modified timestamp, 
	productId varchar2(255 char), 
	quantity number(19,0) not null, 
	startDate timestamp not null, 
	owner_id number(19,0) not null, 
	primary key (id));

create table cp_subscription_attribute (
	cp_subscription_id number(19,0) not null, 
	attributes_id number(19,0) not null, 
	primary key (cp_subscription_id, attributes_id), 
	unique (attributes_id));

create sequence seq_attribute
	Minvalue 1 Maxvalue 1.00000000000000E+27
    	Increment By 1 Start With 1
    	Nocache  Noorder  Nocycle;

create sequence seq_certificate
	Minvalue 1 Maxvalue 1.00000000000000E+27
    	Increment By 1 Start With 1
    	Nocache  Noorder  Nocycle;

create sequence seq_consumer
	Minvalue 1 Maxvalue 1.00000000000000E+27
    	Increment By 1 Start With 1
    	Nocache  Noorder  Nocycle;

create sequence seq_consumer_facts
	Minvalue 1 Maxvalue 1.00000000000000E+27
    	Increment By 1 Start With 1
    	Nocache  Noorder  Nocycle;

create sequence seq_consumer_products
	Minvalue 1 Maxvalue 1.00000000000000E+27
    	Increment By 1 Start With 1
    	Nocache  Noorder  Nocycle;

create sequence seq_consumer_type
	Minvalue 1 Maxvalue 1.00000000000000E+27
    	Increment By 1 Start With 1
    	Nocache  Noorder  Nocycle;

create sequence seq_entitlement
	Minvalue 1 Maxvalue 1.00000000000000E+27
    	Increment By 1 Start With 1
    	Nocache  Noorder  Nocycle;

create sequence seq_entitlement_pool
	Minvalue 1 Maxvalue 1.00000000000000E+27
    	Increment By 1 Start With 1
    	Nocache  Noorder  Nocycle;

create sequence seq_owner
	Minvalue 1 Maxvalue 1.00000000000000E+27
	Increment By 1 Start With 1
	Nocache  Noorder  Nocycle;

create sequence seq_rules
	Minvalue 1 Maxvalue 1.00000000000000E+27
	Increment By 1 Start With 1
	Nocache  Noorder  Nocycle;

create sequence seq_subscription
	Minvalue 1 Maxvalue 1.00000000000000E+27
    	Increment By 1 Start With 1
	Nocache  Noorder  Nocycle;

create sequence seq_id_cert
        Minvalue 1 Maxvalue 1.00000000000000E+27
        Increment By 1 Start With 1
        Nocache  Noorder  Nocycle;

create sequence seq_consumer_ent_cert
        Minvalue 1 Maxvalue 1.00000000000000E+27
        Increment By 1 Start With 1
        Nocache  Noorder  Nocycle;

alter table CP_ATTRIBUTE_HIERARCHY add constraint fk_attribute_parent_id foreign key (PARENT_ATTRIBUTE_ID) references CP_ATTRIBUTE;
alter table CP_ATTRIBUTE_HIERARCHY add constraint fk_attribute_child_id foreign key (CHILD_ATTRIBUTE_ID) references CP_ATTRIBUTE;
alter table CP_CERTIFICATE add constraint fk_certificate_owner foreign key (owner_id) references CP_OWNER;
alter table CP_CONSUMER add constraint FK5820538899BCA368 foreign key (consumer_idcert_id) references CP_ID_CERT;
alter table CP_CONSUMER add constraint FK58205388A53FD653 foreign key (consumer_fact_id) references CP_CONSUMER_FACTS;
alter table CP_CONSUMER add constraint fk_consumer_consumer_type foreign key (type_id) references CP_CONSUMER_TYPE;
alter table CP_CONSUMER add constraint fk_consumer_owner foreign key (owner_id) references CP_OWNER;
alter table CP_CONSUMER add constraint FK58205388F6CFEF28 foreign key (parent_consumer_id) references CP_CONSUMER;
alter table CP_CONSUMER_ENT_CERTIFICATE add constraint fk_cert_entitlement foreign key (entitlement_id) references CP_ENTITLEMENT;
alter table CP_CONSUMER_ENTITLEMENTS add constraint fk_consumer_id foreign key (consumer_id) references CP_CONSUMER;
alter table CP_CONSUMER_ENTITLEMENTS add constraint FKDF174C3D42FFCA77 foreign key (entitlement_id) references CP_ENTITLEMENT;
alter table CP_CONSUMER_FACTS_METADATA add constraint FK53A1D67E2B7FDD78 foreign key (cp_consumer_facts_id) references CP_CONSUMER_FACTS;
alter table CP_ENTITLEMENT add constraint fk_entitlement_ent_pool foreign key (pool_id) references CP_POOL;
alter table CP_ENTITLEMENT add constraint fk_entitlement_owner foreign key (owner_id) references CP_OWNER;
alter table CP_ENTITLEMENT_POOL_ATTRIBUTE add constraint FKC9F1FA9D3EDB0D2B foreign key (cp_pool_id) references CP_POOL;
alter table CP_ENTITLEMENT_POOL_ATTRIBUTE add constraint FKC9F1FA9D16CB6BC foreign key (attributes_id) references CP_ATTRIBUTE;
alter table CP_POOL add constraint fk_pool_source_entitlement foreign key (sourceEntitlement_id) references CP_ENTITLEMENT;
alter table CP_POOL add constraint fk_pool_owner foreign key (owner_id) references CP_OWNER;
alter table CP_PRODUCT_ATTRIBUTE add constraint FK898DE7FAD53844C9 foreign key (cp_product_id) references CP_PRODUCT;
alter table CP_PRODUCT_ATTRIBUTE add constraint FK898DE7FA16CB6BC foreign key (attributes_id) references CP_ATTRIBUTE;
alter table CP_PRODUCT_HIERARCHY add constraint fk_product_child_product_id foreign key (child_product_id) references CP_PRODUCT;
alter table CP_PRODUCT_HIERARCHY add constraint fk_product_product_id foreign key (parent_product_id) references CP_PRODUCT;
alter table CP_SUBSCRIPTION add constraint fk_subscription_owner foreign key (owner_id) references CP_OWNER;
alter table CP_SUBSCRIPTION_ATTRIBUTE add constraint FK10B0260CA984608B foreign key (cp_subscription_id) references CP_SUBSCRIPTION;
alter table CP_CONSUMER_FACTS add constraint FK278CE8101186666B foreign key (cp_consumer_id) references CP_CONSUMER;
