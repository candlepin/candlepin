package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(EntitlementCertificate.class)
public abstract class EntitlementCertificate_ extends org.candlepin.model.RevocableCertificate_ {

	public static volatile SingularAttribute<EntitlementCertificate, Entitlement> entitlement;
	public static volatile SingularAttribute<EntitlementCertificate, String> id;

	public static final String ENTITLEMENT = "entitlement";
	public static final String ID = "id";

}

