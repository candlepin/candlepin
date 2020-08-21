package org.candlepin.model;

import java.util.Date;
import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(AbstractHibernateObject.class)
public abstract class AbstractHibernateObject_ {

	public static volatile SingularAttribute<AbstractHibernateObject, Date> created;
	public static volatile SingularAttribute<AbstractHibernateObject, Date> updated;

	public static final String CREATED = "created";
	public static final String UPDATED = "updated";

}

