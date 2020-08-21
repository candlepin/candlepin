package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(AbstractCertificate.class)
public abstract class AbstractCertificate_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<AbstractCertificate, byte[]> cert;
	public static volatile SingularAttribute<AbstractCertificate, byte[]> key;

	public static final String CERT = "cert";
	public static final String KEY = "key";

}

