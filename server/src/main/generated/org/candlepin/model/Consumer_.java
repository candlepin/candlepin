package org.candlepin.model;

import java.util.Date;
import javax.annotation.Generated;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.MapAttribute;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Consumer.class)
public abstract class Consumer_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SetAttribute<Consumer, Entitlement> entitlements;
	public static volatile SingularAttribute<Consumer, String> role;
	public static volatile SingularAttribute<Consumer, String> usage;
	public static volatile SetAttribute<Consumer, ConsumerInstalledProduct> installedProducts;
	public static volatile SingularAttribute<Consumer, String> annotations;
	public static volatile SingularAttribute<Consumer, String> ownerId;
	public static volatile SingularAttribute<Consumer, String> uuid;
	public static volatile MapAttribute<Consumer, String, String> facts;
	public static volatile SingularAttribute<Consumer, String> releaseVer;
	public static volatile SingularAttribute<Consumer, IdentityCertificate> idCert;
	public static volatile SingularAttribute<Consumer, String> entitlementStatus;
	public static volatile SingularAttribute<Consumer, String> environmentId;
	public static volatile SingularAttribute<Consumer, ContentAccessCertificate> contentAccessCert;
	public static volatile SingularAttribute<Consumer, Date> rhCloudProfileModified;
	public static volatile SingularAttribute<Consumer, String> systemPurposeStatus;
	public static volatile SingularAttribute<Consumer, String> id;
	public static volatile SingularAttribute<Consumer, String> contentAccessMode;
	public static volatile SingularAttribute<Consumer, Boolean> autoheal;
	public static volatile SingularAttribute<Consumer, String> complianceStatusHash;
	public static volatile SingularAttribute<Consumer, Owner> owner;
	public static volatile SingularAttribute<Consumer, Long> entitlementCount;
	public static volatile SetAttribute<Consumer, ConsumerCapability> capabilities;
	public static volatile SetAttribute<Consumer, String> addOns;
	public static volatile SingularAttribute<Consumer, String> systemPurposeStatusHash;
	public static volatile SingularAttribute<Consumer, String> serviceLevel;
	public static volatile SingularAttribute<Consumer, HypervisorId> hypervisorId;
	public static volatile ListAttribute<Consumer, GuestId> guestIds;
	public static volatile SingularAttribute<Consumer, String> name;
	public static volatile SingularAttribute<Consumer, String> typeId;
	public static volatile SingularAttribute<Consumer, KeyPair> keyPair;
	public static volatile SingularAttribute<Consumer, Date> lastCheckin;
	public static volatile SingularAttribute<Consumer, String> username;
	public static volatile SetAttribute<Consumer, String> contentTags;

	public static final String ENTITLEMENTS = "entitlements";
	public static final String ROLE = "role";
	public static final String USAGE = "usage";
	public static final String INSTALLED_PRODUCTS = "installedProducts";
	public static final String ANNOTATIONS = "annotations";
	public static final String OWNER_ID = "ownerId";
	public static final String UUID = "uuid";
	public static final String FACTS = "facts";
	public static final String RELEASE_VER = "releaseVer";
	public static final String ID_CERT = "idCert";
	public static final String ENTITLEMENT_STATUS = "entitlementStatus";
	public static final String ENVIRONMENT_ID = "environmentId";
	public static final String CONTENT_ACCESS_CERT = "contentAccessCert";
	public static final String RH_CLOUD_PROFILE_MODIFIED = "rhCloudProfileModified";
	public static final String SYSTEM_PURPOSE_STATUS = "systemPurposeStatus";
	public static final String ID = "id";
	public static final String CONTENT_ACCESS_MODE = "contentAccessMode";
	public static final String AUTOHEAL = "autoheal";
	public static final String COMPLIANCE_STATUS_HASH = "complianceStatusHash";
	public static final String OWNER = "owner";
	public static final String ENTITLEMENT_COUNT = "entitlementCount";
	public static final String CAPABILITIES = "capabilities";
	public static final String ADD_ONS = "addOns";
	public static final String SYSTEM_PURPOSE_STATUS_HASH = "systemPurposeStatusHash";
	public static final String SERVICE_LEVEL = "serviceLevel";
	public static final String HYPERVISOR_ID = "hypervisorId";
	public static final String GUEST_IDS = "guestIds";
	public static final String NAME = "name";
	public static final String TYPE_ID = "typeId";
	public static final String KEY_PAIR = "keyPair";
	public static final String LAST_CHECKIN = "lastCheckin";
	public static final String USERNAME = "username";
	public static final String CONTENT_TAGS = "contentTags";

}

