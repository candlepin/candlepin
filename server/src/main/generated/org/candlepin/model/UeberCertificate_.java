package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(UeberCertificate.class)
public abstract class UeberCertificate_ extends org.candlepin.model.RevocableCertificate_ {

	public static volatile SingularAttribute<UeberCertificate, Owner> owner;
	public static volatile SingularAttribute<UeberCertificate, String> id;

	public static final String OWNER = "owner";
	public static final String ID = "id";

}

