package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(ContentAccessCertificate.class)
public abstract class ContentAccessCertificate_ extends org.candlepin.model.RevocableCertificate_ {

	public static volatile SingularAttribute<ContentAccessCertificate, String> id;
	public static volatile SingularAttribute<ContentAccessCertificate, Consumer> consumer;

	public static final String ID = "id";
	public static final String CONSUMER = "consumer";

}

