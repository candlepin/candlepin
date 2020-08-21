package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(OwnerContent.class)
public abstract class OwnerContent_ {

	public static volatile SingularAttribute<OwnerContent, Owner> owner;
	public static volatile SingularAttribute<OwnerContent, String> contentUuid;
	public static volatile SingularAttribute<OwnerContent, String> ownerId;
	public static volatile SingularAttribute<OwnerContent, Content> content;

	public static final String OWNER = "owner";
	public static final String CONTENT_UUID = "contentUuid";
	public static final String OWNER_ID = "ownerId";
	public static final String CONTENT = "content";

}

