package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.MapAttribute;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Product.class)
public abstract class Product_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<Product, Integer> entityVersion;
	public static volatile SetAttribute<Product, String> dependentProductIds;
	public static volatile SingularAttribute<Product, Long> multiplier;
	public static volatile ListAttribute<Product, ProductContent> productContent;
	public static volatile SetAttribute<Product, Branding> branding;
	public static volatile SingularAttribute<Product, String> name;
	public static volatile MapAttribute<Product, String, String> attributes;
	public static volatile SingularAttribute<Product, String> id;
	public static volatile SingularAttribute<Product, Boolean> locked;
	public static volatile SingularAttribute<Product, String> uuid;

	public static final String ENTITY_VERSION = "entityVersion";
	public static final String DEPENDENT_PRODUCT_IDS = "dependentProductIds";
	public static final String MULTIPLIER = "multiplier";
	public static final String PRODUCT_CONTENT = "productContent";
	public static final String BRANDING = "branding";
	public static final String NAME = "name";
	public static final String ATTRIBUTES = "attributes";
	public static final String ID = "id";
	public static final String LOCKED = "locked";
	public static final String UUID = "uuid";

}

