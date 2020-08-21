package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(DistributorVersionCapability.class)
public abstract class DistributorVersionCapability_ {

	public static volatile SingularAttribute<DistributorVersionCapability, String> name;
	public static volatile SingularAttribute<DistributorVersionCapability, String> id;
	public static volatile SingularAttribute<DistributorVersionCapability, DistributorVersion> distributorVersion;

	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String DISTRIBUTOR_VERSION = "distributorVersion";

}

