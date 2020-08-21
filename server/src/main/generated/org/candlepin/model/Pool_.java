package org.candlepin.model;

import java.util.Date;
import javax.annotation.Generated;
import javax.persistence.metamodel.MapAttribute;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.candlepin.model.Pool.PoolType;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Pool.class)
public abstract class Pool_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SetAttribute<Pool, Entitlement> entitlements;
	public static volatile SingularAttribute<Pool, Long> consumed;
	public static volatile SingularAttribute<Pool, Product> derivedProduct;
	public static volatile SingularAttribute<Pool, String> orderNumber;
	public static volatile SingularAttribute<Pool, Date> endDate;
	public static volatile SingularAttribute<Pool, SubscriptionsCertificate> cert;
	public static volatile SingularAttribute<Pool, PoolType> type;
	public static volatile SingularAttribute<Pool, String> upstreamEntitlementId;
	public static volatile SingularAttribute<Pool, String> upstreamConsumerId;
	public static volatile SingularAttribute<Pool, String> id;
	public static volatile SingularAttribute<Pool, Boolean> locked;
	public static volatile SingularAttribute<Pool, Entitlement> sourceEntitlement;
	public static volatile SingularAttribute<Pool, Boolean> activeSubscription;
	public static volatile SingularAttribute<Pool, Owner> owner;
	public static volatile SingularAttribute<Pool, Long> exported;
	public static volatile SingularAttribute<Pool, Product> product;
	public static volatile SingularAttribute<Pool, Long> quantity;
	public static volatile SetAttribute<Pool, Product> providedProducts;
	public static volatile SingularAttribute<Pool, SourceStack> sourceStack;
	public static volatile SingularAttribute<Pool, String> contractNumber;
	public static volatile SingularAttribute<Pool, String> accountNumber;
	public static volatile SingularAttribute<Pool, Cdn> cdn;
	public static volatile SingularAttribute<Pool, String> upstreamPoolId;
	public static volatile MapAttribute<Pool, String, String> attributes;
	public static volatile SingularAttribute<Pool, String> restrictedToUsername;
	public static volatile SingularAttribute<Pool, Date> startDate;
	public static volatile SetAttribute<Pool, Product> derivedProvidedProducts;
	public static volatile SingularAttribute<Pool, SourceSubscription> sourceSubscription;

	public static final String ENTITLEMENTS = "entitlements";
	public static final String CONSUMED = "consumed";
	public static final String DERIVED_PRODUCT = "derivedProduct";
	public static final String ORDER_NUMBER = "orderNumber";
	public static final String END_DATE = "endDate";
	public static final String CERT = "cert";
	public static final String TYPE = "type";
	public static final String UPSTREAM_ENTITLEMENT_ID = "upstreamEntitlementId";
	public static final String UPSTREAM_CONSUMER_ID = "upstreamConsumerId";
	public static final String ID = "id";
	public static final String LOCKED = "locked";
	public static final String SOURCE_ENTITLEMENT = "sourceEntitlement";
	public static final String ACTIVE_SUBSCRIPTION = "activeSubscription";
	public static final String OWNER = "owner";
	public static final String EXPORTED = "exported";
	public static final String PRODUCT = "product";
	public static final String QUANTITY = "quantity";
	public static final String PROVIDED_PRODUCTS = "providedProducts";
	public static final String SOURCE_STACK = "sourceStack";
	public static final String CONTRACT_NUMBER = "contractNumber";
	public static final String ACCOUNT_NUMBER = "accountNumber";
	public static final String CDN = "cdn";
	public static final String UPSTREAM_POOL_ID = "upstreamPoolId";
	public static final String ATTRIBUTES = "attributes";
	public static final String RESTRICTED_TO_USERNAME = "restrictedToUsername";
	public static final String START_DATE = "startDate";
	public static final String DERIVED_PROVIDED_PRODUCTS = "derivedProvidedProducts";
	public static final String SOURCE_SUBSCRIPTION = "sourceSubscription";

}

