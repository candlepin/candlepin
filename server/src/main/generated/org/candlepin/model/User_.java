package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(User.class)
public abstract class User_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<User, String> hashedPassword;
	public static volatile SetAttribute<User, Role> roles;
	public static volatile SingularAttribute<User, String> id;
	public static volatile SingularAttribute<User, Boolean> superAdmin;
	public static volatile SingularAttribute<User, String> username;

	public static final String HASHED_PASSWORD = "hashedPassword";
	public static final String ROLES = "roles";
	public static final String ID = "id";
	public static final String SUPER_ADMIN = "superAdmin";
	public static final String USERNAME = "username";

}

