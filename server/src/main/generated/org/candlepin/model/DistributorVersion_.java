package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(DistributorVersion.class)
public abstract class DistributorVersion_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SetAttribute<DistributorVersion, DistributorVersionCapability> capabilities;
	public static volatile SingularAttribute<DistributorVersion, String> displayName;
	public static volatile SingularAttribute<DistributorVersion, String> name;
	public static volatile SingularAttribute<DistributorVersion, String> id;

	public static final String CAPABILITIES = "capabilities";
	public static final String DISPLAY_NAME = "displayName";
	public static final String NAME = "name";
	public static final String ID = "id";

}

