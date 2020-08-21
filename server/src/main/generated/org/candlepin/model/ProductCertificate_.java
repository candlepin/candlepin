package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(ProductCertificate.class)
public abstract class ProductCertificate_ extends org.candlepin.model.AbstractCertificate_ {

	public static volatile SingularAttribute<ProductCertificate, Product> product;
	public static volatile SingularAttribute<ProductCertificate, String> id;

	public static final String PRODUCT = "product";
	public static final String ID = "id";

}

