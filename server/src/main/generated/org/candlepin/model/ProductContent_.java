package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(ProductContent.class)
public abstract class ProductContent_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<ProductContent, Product> product;
	public static volatile SingularAttribute<ProductContent, String> id;
	public static volatile SingularAttribute<ProductContent, Content> content;
	public static volatile SingularAttribute<ProductContent, Boolean> enabled;

	public static final String PRODUCT = "product";
	public static final String ID = "id";
	public static final String CONTENT = "content";
	public static final String ENABLED = "enabled";

}

