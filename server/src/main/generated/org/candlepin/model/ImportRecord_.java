package org.candlepin.model;

import java.util.Date;
import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.candlepin.model.ImportRecord.Status;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(ImportRecord.class)
public abstract class ImportRecord_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<ImportRecord, Owner> owner;
	public static volatile SingularAttribute<ImportRecord, String> fileName;
	public static volatile SingularAttribute<ImportRecord, Date> generatedDate;
	public static volatile SingularAttribute<ImportRecord, ImportUpstreamConsumer> upstreamConsumer;
	public static volatile SingularAttribute<ImportRecord, String> id;
	public static volatile SingularAttribute<ImportRecord, String> generatedBy;
	public static volatile SingularAttribute<ImportRecord, String> statusMessage;
	public static volatile SingularAttribute<ImportRecord, Status> status;

	public static final String OWNER = "owner";
	public static final String FILE_NAME = "fileName";
	public static final String GENERATED_DATE = "generatedDate";
	public static final String UPSTREAM_CONSUMER = "upstreamConsumer";
	public static final String ID = "id";
	public static final String GENERATED_BY = "generatedBy";
	public static final String STATUS_MESSAGE = "statusMessage";
	public static final String STATUS = "status";

}

