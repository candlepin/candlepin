package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.candlepin.auth.Access;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(PermissionBlueprint.class)
public abstract class PermissionBlueprint_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<PermissionBlueprint, Owner> owner;
	public static volatile SingularAttribute<PermissionBlueprint, Role> role;
	public static volatile SingularAttribute<PermissionBlueprint, Access> access;
	public static volatile SingularAttribute<PermissionBlueprint, String> id;
	public static volatile SingularAttribute<PermissionBlueprint, PermissionType> type;

	public static final String OWNER = "owner";
	public static final String ROLE = "role";
	public static final String ACCESS = "access";
	public static final String ID = "id";
	public static final String TYPE = "type";

}

