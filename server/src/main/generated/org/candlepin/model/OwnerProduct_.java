package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(OwnerProduct.class)
public abstract class OwnerProduct_ {

	public static volatile SingularAttribute<OwnerProduct, Owner> owner;
	public static volatile SingularAttribute<OwnerProduct, Product> product;
	public static volatile SingularAttribute<OwnerProduct, String> productUuid;
	public static volatile SingularAttribute<OwnerProduct, String> ownerId;

	public static final String OWNER = "owner";
	public static final String PRODUCT = "product";
	public static final String PRODUCT_UUID = "productUuid";
	public static final String OWNER_ID = "ownerId";

}

