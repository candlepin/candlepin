package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(SubscriptionsCertificate.class)
public abstract class SubscriptionsCertificate_ extends org.candlepin.model.AbstractCertificate_ {

	public static volatile SingularAttribute<SubscriptionsCertificate, CertificateSerial> serial;
	public static volatile SingularAttribute<SubscriptionsCertificate, String> id;

	public static final String SERIAL = "serial";
	public static final String ID = "id";

}

