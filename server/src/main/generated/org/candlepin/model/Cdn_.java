package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Cdn.class)
public abstract class Cdn_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<Cdn, String> name;
	public static volatile SingularAttribute<Cdn, CdnCertificate> cert;
	public static volatile SingularAttribute<Cdn, String> id;
	public static volatile SingularAttribute<Cdn, String> label;
	public static volatile SingularAttribute<Cdn, String> url;

	public static final String NAME = "name";
	public static final String CERT = "cert";
	public static final String ID = "id";
	public static final String LABEL = "label";
	public static final String URL = "url";

}

