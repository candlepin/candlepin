package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(HypervisorId.class)
public abstract class HypervisorId_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<HypervisorId, Owner> owner;
	public static volatile SingularAttribute<HypervisorId, String> reporterId;
	public static volatile SingularAttribute<HypervisorId, String> id;
	public static volatile SingularAttribute<HypervisorId, String> hypervisorId;
	public static volatile SingularAttribute<HypervisorId, Consumer> consumer;

	public static final String OWNER = "owner";
	public static final String REPORTER_ID = "reporterId";
	public static final String ID = "id";
	public static final String HYPERVISOR_ID = "hypervisorId";
	public static final String CONSUMER = "consumer";

}

