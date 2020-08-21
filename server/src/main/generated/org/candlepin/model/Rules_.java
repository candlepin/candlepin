package org.candlepin.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.candlepin.model.Rules.RulesSourceEnum;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Rules.class)
public abstract class Rules_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<Rules, String> rules;
	public static volatile SingularAttribute<Rules, String> id;
	public static volatile SingularAttribute<Rules, RulesSourceEnum> rulesSource;
	public static volatile SingularAttribute<Rules, String> version;

	public static final String RULES = "rules";
	public static final String ID = "id";
	public static final String RULES_SOURCE = "rulesSource";
	public static final String VERSION = "version";

}

