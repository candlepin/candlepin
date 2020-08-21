package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(UpstreamConsumer.class)
public abstract class UpstreamConsumer_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<UpstreamConsumer, IdentityCertificate> idCert;
	public static volatile SingularAttribute<UpstreamConsumer, String> prefixUrlApi;
	public static volatile SingularAttribute<UpstreamConsumer, String> name;
	public static volatile SingularAttribute<UpstreamConsumer, String> id;
	public static volatile SingularAttribute<UpstreamConsumer, String> contentAccessMode;
	public static volatile SingularAttribute<UpstreamConsumer, ConsumerType> type;
	public static volatile SingularAttribute<UpstreamConsumer, String> ownerId;
	public static volatile SingularAttribute<UpstreamConsumer, String> uuid;
	public static volatile SingularAttribute<UpstreamConsumer, String> prefixUrlWeb;

	public static final String ID_CERT = "idCert";
	public static final String PREFIX_URL_API = "prefixUrlApi";
	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String CONTENT_ACCESS_MODE = "contentAccessMode";
	public static final String TYPE = "type";
	public static final String OWNER_ID = "ownerId";
	public static final String UUID = "uuid";
	public static final String PREFIX_URL_WEB = "prefixUrlWeb";

}

