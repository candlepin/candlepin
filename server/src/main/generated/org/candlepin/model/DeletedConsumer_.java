package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(DeletedConsumer.class)
public abstract class DeletedConsumer_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<DeletedConsumer, String> consumerUuid;
	public static volatile SingularAttribute<DeletedConsumer, String> ownerDisplayName;
	public static volatile SingularAttribute<DeletedConsumer, String> principalName;
	public static volatile SingularAttribute<DeletedConsumer, String> id;
	public static volatile SingularAttribute<DeletedConsumer, String> ownerId;
	public static volatile SingularAttribute<DeletedConsumer, String> ownerKey;

	public static final String CONSUMER_UUID = "consumerUuid";
	public static final String OWNER_DISPLAY_NAME = "ownerDisplayName";
	public static final String PRINCIPAL_NAME = "principalName";
	public static final String ID = "id";
	public static final String OWNER_ID = "ownerId";
	public static final String OWNER_KEY = "ownerKey";

}

