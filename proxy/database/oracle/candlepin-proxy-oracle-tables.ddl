create table cp_entitlement_pool_attribute (
	cp_entitlement_pool_id number(19,0) not null, 
	attributes_id number(19,0) not null, 
	unique (attributes_id),
	constraint "CANDLEPIN_ENT_ATTR_PK" primary key (cp_entitlement_pool_id, attributes_id) USING INDEX PCTFREE 10 INITRANS 32 TABLESPACE "CANDLEPIN_IND"
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_product_attribute (
	cp_product_id varchar2(255) not null, 
	attributes_id number(19,0) not null, 
	unique (attributes_id),
	constraint "CANDLEPIN_PROD_ATTR_PK" primary key (cp_product_id, attributes_id) USING INDEX PCTFREE 10 INITRANS 32 TABLESPACE "CANDLEPIN_IND"
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_subscription_attribute (
	cp_subscription_id number(19,0) not null,
	attributes_id number(19,0) not null, 
	unique (attributes_id),
	constraint "CANDLEPIN_SUB_ATTR_PK" primary key (cp_subscription_id, attributes_id) USING INDEX PCTFREE 10 INITRANS 32 TABLESPACE "CANDLEPIN_IND"
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_attribute (id number(19,0) not null,
	name varchar2(255) not null, 
	quantity number(19,0), 
	primary key (id)
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_certificate (
	id number(19,0) not null, 
	certificate_blob clob, 
	owner_id number(19,0) not null,
	constraint "CANDLEPIN_CP_CERT_PK" primary key (id) USING INDEX PCTFREE 10 INITRANS 32 TABLESPACE "CANDLEPIN_IND"
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_consumer (
	id number(19,0) not null, 
	name varchar2(255) not null, 
	uuid varchar2(255) not null unique, 
	consumer_fact_id number(19,0), 
	owner_id number(19,0) not null, 
	parent_consumer_id number(19,0), 
	type_id number(19,0) not null,
	constraint "CANDLEPIN_CP_CONS_PK" primary key (id) USING INDEX PCTFREE 10 INITRANS 32 TABLESPACE "CANDLEPIN_IND"
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_consumer_entitlements (
	consumer_id number(19,0), 
	entitlement_id number(19,0) not null,
	constraint "CANDLEPIN_CP_CONS_ENT_PK1" primary key (entitlement_id) USING INDEX PCTFREE 10 INITRANS 32 TABLESPACE "CANDLEPIN_IND"
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_consumer_facts (
	id number(19,0) not null, 
	primary key (id)
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_consumer_facts_metadata (
	cp_consumer_facts_id number(19,0) not null, 
	element varchar2(255), 
	mapkey varchar2(255),
	constraint "CANDLEPIN_CP_CONS_META_PK" primary key (cp_consumer_facts_id, mapkey)  USING INDEX PCTFREE 10 INITRANS 32 TABLESPACE "CANDLEPIN_IND"
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_consumer_products (
	id number(19,0) not null, 
	productId varchar2(255) not null, 
	consumer_id number(19,0), 
	constraint "CANDLEPIN_CP_CONS_PROD_PK" primary key (id) USING INDEX PCTFREE 10 INITRANS 32 TABLESPACE "CANDLEPIN_IND"
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_consumer_type (
	id number(19,0) not null, 
	label varchar2(255) not null unique, 
	primary key (id)
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_entitlement (
	id number(19,0) not null, 
	isFree number(1,0), 
	startDate date, 
	owner_id number(19,0) not null,
	pool_id number(19,0) not null,
	constraint "CANDLEPIN_CP_ENT_PK" primary key (id)
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_entitlement_pool (
	id number(19,0) not null, 
	activeSubscription number(1,0), 
	currentMembers number(19,0) not null, 
	endDate date not null, 
	maxMembers number(19,0) not null, 
	productId varchar2(255), 
	startDate date not null, 
	subscriptionId number(19,0), 
	consumer_id number(19,0),
	owner_id number(19,0) not null, 
	sourceEntitlement_id number(19,0),
	constraint "CANDLEPIN_ENT_POOL_PK" primary key (id) USING INDEX PCTFREE 10 INITRANS 32 TABLESPACE "CANDLEPIN_IND"
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_owner (
	id number(19,0) not null, 
	name varchar2(255) not null unique, 
	primary key (id)
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_product (
	id varchar2(255) not null, 
	label varchar2(255) not null unique, 
	name varchar2(255) not null unique, 
	primary key (id)
)INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_product_hierarchy (
	PARENT_PRODUCT_ID varchar2(255) not null, 
	CHILD_PRODUCT_ID varchar2(255) not null, 
	unique (CHILD_PRODUCT_ID),
	constraint "CANDLEPIN_CP_PROD_HIER" primary key (PARENT_PRODUCT_ID, CHILD_PRODUCT_ID) USING INDEX PCTFREE 10 INITRANS 32 TABLESPACE "CANDLEPIN_IND"
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_rules (
	id number(19,0) not null, 
	rules_blob clob, 
	primary key (id)
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_subscription (
	id number(19,0) not null, 
	endDate date not null, 
	modified date, 
	productId varchar2(255), 
	quantity number(19,0) not null, 
	startDate date not null, 
	owner_id number(19,0) not null, 
	constraint "CANDLEPIN_CP_SUBS_PK" primary key (id) USING INDEX PCTFREE 10 INITRANS 32 TABLESPACE "CANDLEPIN_IND"
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

create table cp_consumer_idcertificate (
        id number(19,0) not null,
        key BLOB not null,
        pem BLOB not null,
        serialNumber number(19,2) not null,
        constraint "CANDLEPIN_CP_CONS_IDCERT_PK" primary key (id) USING INDEX PCTFREE 10 INITRANS 32 TABLESPACE "CANDLEPIN_IND"
)
INITRANS 32 TABLESPACE "CANDLEPIN_DATA";

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

create sequence seq_rulesi
	Minvalue 1 Maxvalue 1.00000000000000E+27
	Increment By 1 Start With 1
	Nocache  Noorder  Nocycle;

create sequence seq_subscription
	Minvalue 1 Maxvalue 1.00000000000000E+27
    	Increment By 1 Start With 1
	Nocache  Noorder  Nocycle;

create sequence seq_consumer_idcert
        Minvalue 1 Maxvalue 1.00000000000000E+27
        Increment By 1 Start With 1
        Nocache  Noorder  Nocycle;

alter table cp_entitlement_pool_attribute add constraint "CANDLEPIN_ENT_ATTR_FK1" foreign key (cp_entitlement_pool_id) references "cp_entitlement_pool";
alter table cp_entitlement_pool_attribute add constraint "CANDLEPIN_ENT_ATTR_FK2" foreign key (attributes_id) references "cp_attribute";
alter table cp_product_attribute constraint "CANDLEPIN_PROD_ATTR_FK1" foreign key (cp_product_id) references "cp_product";
alter table cp_product_attribute constraint "CANDLEPIN_PROD_ATTR_FK2" foreign key (attributes_id) references "cp_attribute";
alter table cp_subscription_attribute constraint "CANDLEPIN_SUB_ATTR_FK1" foreign key (cp_subscription_id) references "cp_subscription";
alter table cp_subscription_attribute constraint "CANDLEPIN_SUB_ATTR_FK2" foreign key (attributes_id) references "cp_attribute";
alter table cp_certificate constraint "fk_certificate_owner" foreign key (owner_id) references "cp_owner";
alter table cp_consumer constraint "CANDLEPIN_CP_CONS_FK1" foreign key (consumer_fact_id) references "cp_consumer_facts";
alter table cp_consumer constraint "fk_consumer_consumer_type" foreign key (type_id) references "cp_consumer_type";
alter table cp_consumer constraint "fk_consumer_owner" foreign key (owner_id) references "cp_owner";
alter table cp_consumer constraint "CANDLEPIN_CP_CONS_FK4" foreign key (parent_consumer_id) references "cp_consumer";
alter table cp_consumer_entitlements constraint "fk_consumer_id" foreign key (consumer_id) references "cp_consumer";
alter table cp_consumer_entitlements constraint "CANDLEPIN_CP_CONS_ENT_FK1" foreign key (entitlement_id) references "cp_entitlement";
alter table cp_consumer_facts_metadata constraint "CANDLEPIN_CP_CONS_META_FK1" foreign key (cp_consumer_facts_id) references "cp_consumer_facts";
alter table cp_consumer_products constraint "fk_consumer_product_owner" foreign key (consumer_id) references "cp_consumer";
alter table constraint "fk_entitlement_entitlement_pool" foreign key (pool_id) references "cp_entitlement_pool";
alter table constraint "fk_entitlement_owner" foreign key (owner_id) references "cp_owner";
alter table cp_entitlement_pool constraint "fk_entitlement_pool_source_entitlement" foreign key (sourceEntitlement_id) references "cp_entitlement";
alter table cp_entitlement_pool constraint "fk_entitlement_pool_consumer" foreign key (consumer_id) references "cp_consumer";
alter table constraint "fk_user_owner_id" foreign key (owner_id) references "cp_owner";
alter table constraint "fk_product_child_product_id" foreign key (CHILD_PRODUCT_ID) references "cp_product";
alter table constraint "fk_product_product_id" foreign key (PARENT_PRODUCT_ID) references "cp_product";
