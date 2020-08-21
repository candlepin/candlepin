package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(ConsumerCapability.class)
public abstract class ConsumerCapability_ {

	public static volatile SingularAttribute<ConsumerCapability, String> name;
	public static volatile SingularAttribute<ConsumerCapability, String> id;
	public static volatile SingularAttribute<ConsumerCapability, Consumer> consumer;

	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String CONSUMER = "consumer";

}

