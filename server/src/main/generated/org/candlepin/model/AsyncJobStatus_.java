package org.candlepin.model;

import java.util.Date;
import javax.annotation.Generated;
import javax.persistence.metamodel.MapAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.candlepin.model.AsyncJobStatus.JobState;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(AsyncJobStatus.class)
public abstract class AsyncJobStatus_ extends org.candlepin.model.AbstractHibernateObject_ {

	public static volatile SingularAttribute<AsyncJobStatus, Owner> owner;
	public static volatile SingularAttribute<AsyncJobStatus, String> origin;
	public static volatile SingularAttribute<AsyncJobStatus, String> jobKey;
	public static volatile SingularAttribute<AsyncJobStatus, String> ownerId;
	public static volatile SingularAttribute<AsyncJobStatus, Integer> version;
	public static volatile SingularAttribute<AsyncJobStatus, Boolean> logExecutionDetails;
	public static volatile SingularAttribute<AsyncJobStatus, JobState> previousState;
	public static volatile SingularAttribute<AsyncJobStatus, String> principal;
	public static volatile SingularAttribute<AsyncJobStatus, String> result;
	public static volatile SingularAttribute<AsyncJobStatus, Integer> maxAttempts;
	public static volatile SingularAttribute<AsyncJobStatus, String> logLevel;
	public static volatile SingularAttribute<AsyncJobStatus, String> executor;
	public static volatile SingularAttribute<AsyncJobStatus, String> name;
	public static volatile SingularAttribute<AsyncJobStatus, String> correlationId;
	public static volatile SingularAttribute<AsyncJobStatus, Date> startTime;
	public static volatile MapAttribute<AsyncJobStatus, String, String> arguments;
	public static volatile SingularAttribute<AsyncJobStatus, String> id;
	public static volatile SingularAttribute<AsyncJobStatus, JobState> state;
	public static volatile SingularAttribute<AsyncJobStatus, Date> endTime;
	public static volatile SingularAttribute<AsyncJobStatus, String> group;
	public static volatile SingularAttribute<AsyncJobStatus, Integer> attempts;

	public static final String OWNER = "owner";
	public static final String ORIGIN = "origin";
	public static final String JOB_KEY = "jobKey";
	public static final String OWNER_ID = "ownerId";
	public static final String VERSION = "version";
	public static final String LOG_EXECUTION_DETAILS = "logExecutionDetails";
	public static final String PREVIOUS_STATE = "previousState";
	public static final String PRINCIPAL = "principal";
	public static final String RESULT = "result";
	public static final String MAX_ATTEMPTS = "maxAttempts";
	public static final String LOG_LEVEL = "logLevel";
	public static final String EXECUTOR = "executor";
	public static final String NAME = "name";
	public static final String CORRELATION_ID = "correlationId";
	public static final String START_TIME = "startTime";
	public static final String ARGUMENTS = "arguments";
	public static final String ID = "id";
	public static final String STATE = "state";
	public static final String END_TIME = "endTime";
	public static final String GROUP = "group";
	public static final String ATTEMPTS = "attempts";

}

