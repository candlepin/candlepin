package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Environment.class)
public abstract class Environment_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<Environment, Owner> owner;
	public static volatile SingularAttribute<Environment, String> name;
	public static volatile SingularAttribute<Environment, String> description;
	public static volatile SingularAttribute<Environment, String> id;
	public static volatile SetAttribute<Environment, EnvironmentContent> environmentContent;

	public static final String OWNER = "owner";
	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";
	public static final String ID = "id";
	public static final String ENVIRONMENT_CONTENT = "environmentContent";

}

