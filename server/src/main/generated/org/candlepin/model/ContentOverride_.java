package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(ContentOverride.class)
public abstract class ContentOverride_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<ContentOverride, String> name;
	public static volatile SingularAttribute<ContentOverride, String> id;
	public static volatile SingularAttribute<ContentOverride, String> contentLabel;
	public static volatile SingularAttribute<ContentOverride, String> value;

	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String CONTENT_LABEL = "contentLabel";
	public static final String VALUE = "value";

}

