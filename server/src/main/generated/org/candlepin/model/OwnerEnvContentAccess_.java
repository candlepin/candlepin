package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(OwnerEnvContentAccess.class)
public abstract class OwnerEnvContentAccess_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<OwnerEnvContentAccess, Owner> owner;
	public static volatile SingularAttribute<OwnerEnvContentAccess, Environment> environment;
	public static volatile SingularAttribute<OwnerEnvContentAccess, String> contentJson;
	public static volatile SingularAttribute<OwnerEnvContentAccess, String> id;

	public static final String OWNER = "owner";
	public static final String ENVIRONMENT = "environment";
	public static final String CONTENT_JSON = "contentJson";
	public static final String ID = "id";

}

