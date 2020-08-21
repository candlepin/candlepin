package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(PoolAttribute.class)
public abstract class PoolAttribute_ {

	public static volatile SingularAttribute<PoolAttribute, String> name;
	public static volatile SingularAttribute<PoolAttribute, String> poolId;
	public static volatile SingularAttribute<PoolAttribute, String> value;

	public static final String NAME = "name";
	public static final String POOL_ID = "poolId";
	public static final String VALUE = "value";

}

