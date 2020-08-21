package org.candlepin.model.activationkeys;

import javax.annotation.Generated;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(ActivationKey.class)
public abstract class ActivationKey_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<ActivationKey, Owner> owner;
	public static volatile SingularAttribute<ActivationKey, String> role;
	public static volatile SetAttribute<ActivationKey, String> addOns;
	public static volatile SingularAttribute<ActivationKey, String> usage;
	public static volatile SingularAttribute<ActivationKey, String> description;
	public static volatile SetAttribute<ActivationKey, ActivationKeyPool> pools;
	public static volatile SingularAttribute<ActivationKey, String> serviceLevel;
	public static volatile SingularAttribute<ActivationKey, String> releaseVer;
	public static volatile SingularAttribute<ActivationKey, Boolean> autoAttach;
	public static volatile SetAttribute<ActivationKey, Product> products;
	public static volatile SetAttribute<ActivationKey, ActivationKeyContentOverride> contentOverrides;
	public static volatile SingularAttribute<ActivationKey, String> name;
	public static volatile SingularAttribute<ActivationKey, String> id;

	public static final String OWNER = "owner";
	public static final String ROLE = "role";
	public static final String ADD_ONS = "addOns";
	public static final String USAGE = "usage";
	public static final String DESCRIPTION = "description";
	public static final String POOLS = "pools";
	public static final String SERVICE_LEVEL = "serviceLevel";
	public static final String RELEASE_VER = "releaseVer";
	public static final String AUTO_ATTACH = "autoAttach";
	public static final String PRODUCTS = "products";
	public static final String CONTENT_OVERRIDES = "contentOverrides";
	public static final String NAME = "name";
	public static final String ID = "id";

}

