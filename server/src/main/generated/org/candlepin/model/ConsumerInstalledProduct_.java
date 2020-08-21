package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(ConsumerInstalledProduct.class)
public abstract class ConsumerInstalledProduct_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<ConsumerInstalledProduct, String> productId;
	public static volatile SingularAttribute<ConsumerInstalledProduct, String> id;
	public static volatile SingularAttribute<ConsumerInstalledProduct, String> arch;
	public static volatile SingularAttribute<ConsumerInstalledProduct, String> version;
	public static volatile SingularAttribute<ConsumerInstalledProduct, String> productName;
	public static volatile SingularAttribute<ConsumerInstalledProduct, Consumer> consumer;

	public static final String PRODUCT_ID = "productId";
	public static final String ID = "id";
	public static final String ARCH = "arch";
	public static final String VERSION = "version";
	public static final String PRODUCT_NAME = "productName";
	public static final String CONSUMER = "consumer";

}

