package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Branding.class)
public abstract class Branding_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<Branding, Product> product;
	public static volatile SingularAttribute<Branding, String> productId;
	public static volatile SingularAttribute<Branding, String> name;
	public static volatile SingularAttribute<Branding, String> id;
	public static volatile SingularAttribute<Branding, String> type;

	public static final String PRODUCT = "product";
	public static final String PRODUCT_ID = "productId";
	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String TYPE = "type";

}

