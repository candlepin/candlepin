package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(EnvironmentContent.class)
public abstract class EnvironmentContent_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<EnvironmentContent, Environment> environment;
	public static volatile SingularAttribute<EnvironmentContent, String> id;
	public static volatile SingularAttribute<EnvironmentContent, Content> content;
	public static volatile SingularAttribute<EnvironmentContent, Boolean> enabled;

	public static final String ENVIRONMENT = "environment";
	public static final String ID = "id";
	public static final String CONTENT = "content";
	public static final String ENABLED = "enabled";

}

