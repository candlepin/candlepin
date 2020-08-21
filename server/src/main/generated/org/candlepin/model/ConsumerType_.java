package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(ConsumerType.class)
public abstract class ConsumerType_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<ConsumerType, Boolean> manifest;
	public static volatile SingularAttribute<ConsumerType, String> id;
	public static volatile SingularAttribute<ConsumerType, String> label;

	public static final String MANIFEST = "manifest";
	public static final String ID = "id";
	public static final String LABEL = "label";

}

