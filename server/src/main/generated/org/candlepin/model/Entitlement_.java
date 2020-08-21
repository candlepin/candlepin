package org.candlepin.model;

import java.util.Date;
import javax.annotation.Generated;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Entitlement.class)
public abstract class Entitlement_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<Entitlement, Owner> owner;
	public static volatile SingularAttribute<Entitlement, Boolean> dirty;
	public static volatile SingularAttribute<Entitlement, Integer> quantity;
	public static volatile SetAttribute<Entitlement, EntitlementCertificate> certificates;
	public static volatile SingularAttribute<Entitlement, Pool> pool;
	public static volatile SingularAttribute<Entitlement, Date> endDateOverride;
	public static volatile SingularAttribute<Entitlement, Boolean> updatedOnStart;
	public static volatile SingularAttribute<Entitlement, String> id;
	public static volatile SingularAttribute<Entitlement, Consumer> consumer;

	public static final String OWNER = "owner";
	public static final String DIRTY = "dirty";
	public static final String QUANTITY = "quantity";
	public static final String CERTIFICATES = "certificates";
	public static final String POOL = "pool";
	public static final String END_DATE_OVERRIDE = "endDateOverride";
	public static final String UPDATED_ON_START = "updatedOnStart";
	public static final String ID = "id";
	public static final String CONSUMER = "consumer";

}

