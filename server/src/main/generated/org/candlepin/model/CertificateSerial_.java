package org.candlepin.model;

import java.util.Date;
import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(CertificateSerial.class)
public abstract class CertificateSerial_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<CertificateSerial, Boolean> collected;
	public static volatile SingularAttribute<CertificateSerial, Date> expiration;
	public static volatile SingularAttribute<CertificateSerial, Long> id;
	public static volatile SingularAttribute<CertificateSerial, Boolean> revoked;

	public static final String COLLECTED = "collected";
	public static final String EXPIRATION = "expiration";
	public static final String ID = "id";
	public static final String REVOKED = "revoked";

}

