package org.candlepin.model.activationkeys;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.candlepin.model.Pool;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(ActivationKeyPool.class)
public abstract class ActivationKeyPool_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<ActivationKeyPool, Long> quantity;
	public static volatile SingularAttribute<ActivationKeyPool, Pool> pool;
	public static volatile SingularAttribute<ActivationKeyPool, String> id;
	public static volatile SingularAttribute<ActivationKeyPool, ActivationKey> key;

	public static final String QUANTITY = "quantity";
	public static final String POOL = "pool";
	public static final String ID = "id";
	public static final String KEY = "key";

}

