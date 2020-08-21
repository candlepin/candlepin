package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(SourceStack.class)
public abstract class SourceStack_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<SourceStack, Consumer> sourceConsumer;
	public static volatile SingularAttribute<SourceStack, String> id;
	public static volatile SingularAttribute<SourceStack, Pool> derivedPool;
	public static volatile SingularAttribute<SourceStack, String> sourceStackId;

	public static final String SOURCE_CONSUMER = "sourceConsumer";
	public static final String ID = "id";
	public static final String DERIVED_POOL = "derivedPool";
	public static final String SOURCE_STACK_ID = "sourceStackId";

}

