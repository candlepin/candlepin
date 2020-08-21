package org.candlepin.model;

import java.util.Date;
import javax.annotation.Generated;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.candlepin.model.activationkeys.ActivationKey;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Owner.class)
public abstract class Owner_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<Owner, String> contentPrefix;
	public static volatile SingularAttribute<Owner, Date> lastRefreshed;
	public static volatile SetAttribute<Owner, Environment> environments;
	public static volatile SingularAttribute<Owner, String> displayName;
	public static volatile SingularAttribute<Owner, String> defaultServiceLevel;
	public static volatile SingularAttribute<Owner, UpstreamConsumer> upstreamConsumer;
	public static volatile SetAttribute<Owner, Pool> pools;
	public static volatile SingularAttribute<Owner, Owner> parentOwner;
	public static volatile SingularAttribute<Owner, Boolean> autobindHypervisorDisabled;
	public static volatile SetAttribute<Owner, ActivationKey> activationKeys;
	public static volatile SingularAttribute<Owner, String> contentAccessModeList;
	public static volatile SingularAttribute<Owner, String> logLevel;
	public static volatile SetAttribute<Owner, Consumer> consumers;
	public static volatile SingularAttribute<Owner, String> id;
	public static volatile SingularAttribute<Owner, String> contentAccessMode;
	public static volatile SingularAttribute<Owner, Boolean> autobindDisabled;
	public static volatile SingularAttribute<Owner, String> key;

	public static final String CONTENT_PREFIX = "contentPrefix";
	public static final String LAST_REFRESHED = "lastRefreshed";
	public static final String ENVIRONMENTS = "environments";
	public static final String DISPLAY_NAME = "displayName";
	public static final String DEFAULT_SERVICE_LEVEL = "defaultServiceLevel";
	public static final String UPSTREAM_CONSUMER = "upstreamConsumer";
	public static final String POOLS = "pools";
	public static final String PARENT_OWNER = "parentOwner";
	public static final String AUTOBIND_HYPERVISOR_DISABLED = "autobindHypervisorDisabled";
	public static final String ACTIVATION_KEYS = "activationKeys";
	public static final String CONTENT_ACCESS_MODE_LIST = "contentAccessModeList";
	public static final String LOG_LEVEL = "logLevel";
	public static final String CONSUMERS = "consumers";
	public static final String ID = "id";
	public static final String CONTENT_ACCESS_MODE = "contentAccessMode";
	public static final String AUTOBIND_DISABLED = "autobindDisabled";
	public static final String KEY = "key";

}

