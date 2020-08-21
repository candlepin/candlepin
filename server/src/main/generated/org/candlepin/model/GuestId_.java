package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.MapAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(GuestId.class)
public abstract class GuestId_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<GuestId, String> guestIdLower;
	public static volatile MapAttribute<GuestId, String, String> attributes;
	public static volatile SingularAttribute<GuestId, String> id;
	public static volatile SingularAttribute<GuestId, String> guestId;
	public static volatile SingularAttribute<GuestId, Consumer> consumer;

	public static final String GUEST_ID_LOWER = "guestIdLower";
	public static final String ATTRIBUTES = "attributes";
	public static final String ID = "id";
	public static final String GUEST_ID = "guestId";
	public static final String CONSUMER = "consumer";

}

