package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(SourceSubscription.class)
public abstract class SourceSubscription_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<SourceSubscription, String> subscriptionSubKey;
	public static volatile SingularAttribute<SourceSubscription, Pool> pool;
	public static volatile SingularAttribute<SourceSubscription, String> id;
	public static volatile SingularAttribute<SourceSubscription, String> subscriptionId;

	public static final String SUBSCRIPTION_SUB_KEY = "subscriptionSubKey";
	public static final String POOL = "pool";
	public static final String ID = "id";
	public static final String SUBSCRIPTION_ID = "subscriptionId";

}

