package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Role.class)
public abstract class Role_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SetAttribute<Role, PermissionBlueprint> permissions;
	public static volatile SingularAttribute<Role, String> name;
	public static volatile SingularAttribute<Role, String> id;
	public static volatile SetAttribute<Role, User> users;

	public static final String PERMISSIONS = "permissions";
	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String USERS = "users";

}

