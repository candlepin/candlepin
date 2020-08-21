package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Content.class)
public abstract class Content_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SetAttribute<Content, String> modifiedProductIds;
	public static volatile SingularAttribute<Content, String> label;
	public static volatile SingularAttribute<Content, String> type;
	public static volatile SingularAttribute<Content, String> gpgUrl;
	public static volatile SingularAttribute<Content, String> uuid;
	public static volatile SingularAttribute<Content, String> releaseVer;
	public static volatile SingularAttribute<Content, Integer> entityVersion;
	public static volatile SingularAttribute<Content, String> contentUrl;
	public static volatile SingularAttribute<Content, String> requiredTags;
	public static volatile SingularAttribute<Content, String> vendor;
	public static volatile SingularAttribute<Content, Long> metadataExpire;
	public static volatile SingularAttribute<Content, String> name;
	public static volatile SingularAttribute<Content, String> id;
	public static volatile SingularAttribute<Content, Boolean> locked;
	public static volatile SingularAttribute<Content, String> arches;

	public static final String MODIFIED_PRODUCT_IDS = "modifiedProductIds";
	public static final String LABEL = "label";
	public static final String TYPE = "type";
	public static final String GPG_URL = "gpgUrl";
	public static final String UUID = "uuid";
	public static final String RELEASE_VER = "releaseVer";
	public static final String ENTITY_VERSION = "entityVersion";
	public static final String CONTENT_URL = "contentUrl";
	public static final String REQUIRED_TAGS = "requiredTags";
	public static final String VENDOR = "vendor";
	public static final String METADATA_EXPIRE = "metadataExpire";
	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String LOCKED = "locked";
	public static final String ARCHES = "arches";

}

