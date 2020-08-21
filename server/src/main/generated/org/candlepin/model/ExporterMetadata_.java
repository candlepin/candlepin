package org.candlepin.model;

import java.util.Date;
import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(ExporterMetadata.class)
public abstract class ExporterMetadata_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<ExporterMetadata, Date> exported;
	public static volatile SingularAttribute<ExporterMetadata, Owner> owner;
	public static volatile SingularAttribute<ExporterMetadata, String> id;
	public static volatile SingularAttribute<ExporterMetadata, String> type;

	public static final String EXPORTED = "exported";
	public static final String OWNER = "owner";
	public static final String ID = "id";
	public static final String TYPE = "type";

}

