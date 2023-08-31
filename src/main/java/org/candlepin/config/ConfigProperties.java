/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.config;

import static org.candlepin.config.ConfigurationPrefixes.JPA_CONFIG_PREFIX;

import org.candlepin.async.tasks.ActiveEntitlementJob;
import org.candlepin.async.tasks.CertificateCleanupJob;
import org.candlepin.async.tasks.CloudAccountOrgSetupJob;
import org.candlepin.async.tasks.EntitlerJob;
import org.candlepin.async.tasks.ExpiredPoolsCleanupJob;
import org.candlepin.async.tasks.ImportRecordCleanerJob;
import org.candlepin.async.tasks.InactiveConsumerCleanerJob;
import org.candlepin.async.tasks.JobCleaner;
import org.candlepin.async.tasks.ManifestCleanerJob;
import org.candlepin.async.tasks.OrphanCleanupJob;
import org.candlepin.async.tasks.UnmappedGuestEntitlementCleanerJob;
import org.candlepin.guice.CandlepinContextListener;

import java.util.HashMap;
import java.util.Map;



/**
 * Defines a map of default properties used to prepopulate the {@link Configuration}.
 * Also holds static keys for config lookup.
 */
public class ConfigProperties {
    private ConfigProperties() {
    }

    public static final String CANDLEPIN_URL = "candlepin.url";

    /**
     * Whether we allow users to authenticate (e.g. HTTP Basic) over insecure
     * channel such as HTTP.
     * The default is false.
     * A user might set this to 'true' for easier debugging of the Candlepin
     * server.
     * If kept in default (false) setting, then Candlepin will disallow any
     * attempt to authenticate over insecure channel.
     */
    public static final String AUTH_OVER_HTTP = "candlepin.allow_auth_over_http";
    public static final String CA_KEY = "candlepin.ca_key";
    public static final String CA_CERT = "candlepin.ca_cert";
    public static final String FAIL_ON_UNKNOWN_IMPORT_PROPERTIES = "candlepin.importer.fail_on_unknown";
    public static final String CA_CERT_UPSTREAM = "candlepin.upstream_ca_cert";
    public static final String CA_KEY_PASSWORD = "candlepin.ca_key_password";

    /*
     * XXX The actual property key refers to HornetQ which was ActiveMQ's ancestor. We have to keep the
     * key unchanged for compatibility reasons. These are deprecated, however, and should be replaced by
     * newer configuration values as features are added.
     */
    public static final String ACTIVEMQ_ENABLED = "candlepin.audit.hornetq.enable";

    public static final String ACTIVEMQ_EMBEDDED = "candlepin.audit.hornetq.embedded";
    public static final String ACTIVEMQ_BROKER_URL = "candlepin.audit.hornetq.broker_url";
    public static final String ACTIVEMQ_SERVER_CONFIG_PATH = "candlepin.audit.hornetq.config_path";

    /**
     * This number is large message size. Any message
     * that is bigger than this number is regarded as large.
     * That means that ActiveMQ will page this message
     * to a disk. Setting this number to something high
     * e.g. 1 000 0000 will effectively disable the
     * functionality. This is handy when you expect a lot of
     * messages and you do not want to run out of disk space.
     *
     * NOTE: Increasing this property to more than 501760 could cause the following issue:
     *  https://access.redhat.com/solutions/2203791
     * As the article explains, this issue could occur only when the value of journal-buffer-size is less
     * than the value of min-large-message-size, in bytes. We do not currently alter the
     * journal-buffer-size on the artemis broker, which uses the default value (501760 bytes), while the
     * default value of this property (which is used to set the min-large-message-size) is set to 102400
     * bytes. As such, the issue will not occur with our default settings, but only if we set this property
     * to a value larger than 501760 (or if the journal-buffer-size property in broker.xml is changed to be
     * less than the value of this property).
     */
    public static final String ACTIVEMQ_LARGE_MSG_SIZE = "candlepin.audit.hornetq.large_msg_size";

    /**
     * The interval (in milliseconds) at which the ActiveMQStatusMonitor will check the connection state
     * once the connection has dropped.
     */
    public static final String ACTIVEMQ_CONNECTION_MONITOR_INTERVAL = "candlepin.audit.hornetq.monitor.interval";

    public static final String AUDIT_LISTENERS = "candlepin.audit.listeners";
    /**
     * Enables audit event filtering. See documentation of EventFilter
     */
    public static final String AUDIT_FILTER_ENABLED = "candlepin.audit.filter.enabled";
    /**
     * Events mentioned in this list will not be filtered. They we be sent into our
     * auditing system
     */
    public static final String AUDIT_FILTER_DO_NOT_FILTER = "candlepin.audit.filter.donotfilter";
    /**
     * These events will be dropped.
     */
    public static final String AUDIT_FILTER_DO_FILTER = "candlepin.audit.filter.dofilter";
    /**
     * Can be set to DO_FILTER or DO_NOT_FILTER.
     * When set to DO_FILTER, then events that are not either in DO_FILTER nor DO_NOT_FILTER
     * will be filtered, meaning they will not enter ActiveMQ.
     */
    public static final String AUDIT_FILTER_DEFAULT_POLICY = "candlepin.audit.filter.policy";

    public static final String PRETTY_PRINT = "candlepin.pretty_print";
    public static final String ACTIVATION_DEBUG_PREFIX = "candlepin.subscription.activation.debug_prefix";

    // Space separated list of resources to hide in the GET / list:
    public static final String HIDDEN_RESOURCES = "candlepin.hidden_resources";

    // Space separated list of resources to hide in GET /status
    public static final String HIDDEN_CAPABILITIES = "candlepin.hidden_capabilities";

    public static final String DB_MANAGE_ON_START = "candlepin.db.database_manage_on_startup";

    // Authentication
    public static final String TRUSTED_AUTHENTICATION = "candlepin.auth.trusted.enable";
    public static final String SSL_AUTHENTICATION = "candlepin.auth.ssl.enable";
    public static final String OAUTH_AUTHENTICATION = "candlepin.auth.oauth.enable";
    public static final String BASIC_AUTHENTICATION = "candlepin.auth.basic.enable";
    public static final String KEYCLOAK_AUTHENTICATION = "candlepin.auth.keycloak.enable";
    public static final String CLOUD_AUTHENTICATION = "candlepin.auth.cloud.enable";
    public static final String ACTIVATION_KEY_AUTHENTICATION = "candlepin.auth.activation_key.enable";

    // JWT configuration
    public static final String JWT_ISSUER = "candlepin.jwt.issuer";
    public static final String JWT_TOKEN_TTL = "candlepin.jwt.token_ttl";
    public static final String ANON_JWT_TOKEN_TTL = "candlepin.jwt.anon_token_ttl";

    /**
     * A possibility to enable Suspend Mode. By default, the suspend mode is enabled
     */
    public static final String SUSPEND_MODE_ENABLED = "candlepin.suspend_mode_enabled";

    // Messaging
    public static final String CPM_PROVIDER = "candlepin.messaging.provider";

    // OCSP stapling
    public static final String SSL_VERIFY = "candlepin.sslverifystatus";

    // TODO:
    // Clean up all the messaging configuration. We have all sorts of prefixes and definitions for
    // common broker options, and stuff which is unique to specific brokers. We should be defining
    // them as "candlepin.messaging.<broker type>.<setting>" instead of the various sections we
    // have now.
    public static final String ACTIVEMQ_EMBEDDED_BROKER = "candlepin.messaging.activemq.embedded.enabled";

    public static final String ACTIVEMQ_JAAS_INVM_LOGIN_NAME = "candlepin.messaging.activemq.embedded.jaas_invm_login_name";
    public static final String ACTIVEMQ_JAAS_CERTIFICATE_LOGIN_NAME = "candlepin.messaging.activemq.embedded.jaas_certificate_login_name";

    // Quartz configurations
    public static final String QUARTZ_CLUSTERED_MODE = "org.quartz.jobStore.isClustered";

    // Hibernate
    public static final String DB_DRIVER_CLASS = JPA_CONFIG_PREFIX + "hibernate.connection.driver_class";
    public static final String DB_URL = JPA_CONFIG_PREFIX + "hibernate.connection.url";
    public static final String DB_USERNAME = JPA_CONFIG_PREFIX + "hibernate.connection.username";
    public static final String DB_PASSWORD = JPA_CONFIG_PREFIX + "hibernate.connection.password";

    // Cache
    public static final String CACHE_JMX_STATS = "cache.jmx.statistics";
    public static final String CACHE_CONFIG_FILE_URI = JPA_CONFIG_PREFIX + "hibernate.javax.cache.uri";

    public static final String[] ENCRYPTED_PROPERTIES = new String[] {
        DB_PASSWORD,
    };

    public static final String SYNC_WORK_DIR = "candlepin.sync.work_dir";

    /**
     *  Controls which facts will be stored by Candlepin -- facts with keys that do not match this
     *  value will be discarded.
     */
    public static final String CONSUMER_FACTS_MATCHER = "candlepin.consumer.facts.match_regex";

    public static final String SHARD_USERNAME = "candlepin.shard.username";
    public static final String SHARD_PASSWORD = "candlepin.shard.password";
    public static final String SHARD_WEBAPP = "candlepin.shard.webapp";

    public static final String STANDALONE = "candlepin.standalone";
    public static final String ENV_CONTENT_FILTERING = "candlepin.environment_content_filtering";
    public static final String USE_SYSTEM_UUID_FOR_MATCHING = "candlepin.use_system_uuid_for_matching";

    public static final String CONSUMER_SYSTEM_NAME_PATTERN = "candlepin.consumer_system_name_pattern";
    public static final String CONSUMER_PERSON_NAME_PATTERN = "candlepin.consumer_person_name_pattern";

    public static final String PREFIX_WEBURL = "candlepin.export.prefix.weburl";
    public static final String PREFIX_APIURL = "candlepin.export.prefix.apiurl";
    public static final String PASSPHRASE_SECRET_FILE = "candlepin.passphrase.path";

    public static final String PRODUCT_CACHE_MAX = "candlepin.cache.product_cache_max";

    public static final String INTEGER_FACTS = "candlepin.integer_facts";
    private static final String INTEGER_FACT_LIST = "";

    public static final String NON_NEG_INTEGER_FACTS = "candlepin.positive_integer_facts";
    private static final String NON_NEG_INTEGER_FACT_LIST = "cpu.core(s)_per_socket," +
        "cpu.cpu(s)," +
        "cpu.cpu_socket(s)," +
        "lscpu.core(s)_per_socket," +
        "lscpu.cpu(s)," +
        "lscpu.numa_node(s)," +
        "lscpu.socket(s)," +
        "lscpu.thread(s)_per_core";

    public static final String INTEGER_ATTRIBUTES = "candlepin.integer_attributes";
    private static final String INTEGER_ATTRIBUTE_LIST = "";

    public static final String NON_NEG_INTEGER_ATTRIBUTES = "candlepin.positive_integer_attributes";
    private static final String NON_NEG_INTEGER_ATTRIBUTE_LIST = "sockets," +
        "warning_period," +
        "ram," +
        "cores";

    public static final String LONG_ATTRIBUTES = "candlepin.long_attributes";
    private static final String LONG_ATTRIBUTE_LIST = "";

    public static final String NON_NEG_LONG_ATTRIBUTES = "candlepin.positive_long_attributes";
    private static final String NON_NEG_LONG_ATTRIBUTE_LIST = "metadata_expire";

    public static final String BOOLEAN_ATTRIBUTES = "candlepin.boolean_attributes";
    private static final String BOOLEAN_ATTRIBUTE_LIST = "management_enabled," +
        "virt_only";

    public static final String IDENTITY_CERT_YEAR_ADDENDUM = "candlepin.identityCert.yr.addendum";
    /**
     * Identity certificate expiry threshold in days
     */
    public static final String IDENTITY_CERT_EXPIRY_THRESHOLD = "candlepin.identityCert.expiry.threshold";

    public static final String SWAGGER_ENABLED = "candlepin.swagger.enabled";

    /** Enabled dev page used to interactively login to a Keycloak instance and generate offline token. */
    public static final String TOKENPAGE_ENABLED = "candlepin.tokenpage.enabled";

    /** Path to keycloak.json */
    public static final String KEYCLOAK_FILEPATH = "candlepin.keycloak.config";

    // Async Job Properties and utilities
    public static final String ASYNC_JOBS_NODE_NAME = "candlepin.async.node_name";
    public static final String ASYNC_JOBS_THREADS = "candlepin.async.threads";
    public static final String ASYNC_JOBS_SCHEDULER_ENABLED = "candlepin.async.scheduler.enabled";

    public static final String ASYNC_JOBS_DISPATCH_ADDRESS = "candlepin.async.dispatch_address";
    public static final String ASYNC_JOBS_RECEIVE_ADDRESS = "candlepin.async.receive_address";
    public static final String ASYNC_JOBS_RECEIVE_FILTER = "candlepin.async.receive_filter";

    // Whether or not we should allow queuing new jobs on this node while the job manager is
    // suspended/paused
    public static final String ASYNC_JOBS_QUEUE_WHILE_SUSPENDED = "candlepin.async.queue_while_suspended";

    // Used for per-job configuration. The full syntax is "PREFIX.{job_key}.SUFFIX". For instance,
    // to configure the schedule flag for the job TestJob1, the full configuration would be:
    // candlepin.async.jobs.TestJob1.schedule=0 0 0/3 * * ?
    public static final String ASYNC_JOBS_PREFIX = "candlepin.async.jobs.";
    public static final String ASYNC_JOBS_JOB_SCHEDULE = "schedule";

    // Special value used to denote a job's schedule should be manual rather than automatic.
    public static final String ASYNC_JOBS_MANUAL_SCHEDULE = "manual";

    // "Temporary" configuration to limit the scope of the jobs/schedule endpoint. Only job keys
    // specified in this property will be allowed to be triggered via the schedule endpoint.
    public static final String ASYNC_JOBS_TRIGGERABLE_JOBS = "candlepin.async.triggerable_jobs";
    public static final String[] ASYNC_JOBS_TRIGGERABLE_JOBS_LIST = new String[] {
        ActiveEntitlementJob.JOB_KEY,
        CertificateCleanupJob.JOB_KEY,
        ExpiredPoolsCleanupJob.JOB_KEY,
        ImportRecordCleanerJob.JOB_KEY,
        JobCleaner.JOB_KEY,
        ManifestCleanerJob.JOB_KEY,
        OrphanCleanupJob.JOB_KEY,
        UnmappedGuestEntitlementCleanerJob.JOB_KEY,
        InactiveConsumerCleanerJob.JOB_KEY,
        CloudAccountOrgSetupJob.JOB_KEY
    };

    // How long (in seconds) to wait for job threads to finish during a graceful Tomcat shutdown
    public static final String ASYNC_JOBS_THREAD_SHUTDOWN_TIMEOUT = "candlepin.async.thread.shutdown.timeout";

    // How many days to allow a product to be orphaned before removing it on the next refresh or
    // manifest import. Default: 30 days
    public static final String ORPHANED_ENTITY_GRACE_PERIOD = "candlepin.refresh.orphan_entity_grace_period";

    /**
     * Fetches a string representing the prefix for all per-job configuration for the specified job.
     * The job key or class name may be used, but the usage must be consistent.
     *
     * @param jobKey
     *  the key or class name of the job for which to fetch build configuration prefix
     *
     * @return
     *  the configuration prefix for the given job
     */
    public static String jobConfigPrefix(String jobKey) {
        StringBuilder builder = new StringBuilder(ASYNC_JOBS_PREFIX)
            .append(jobKey)
            .append('.');

        return builder.toString();
    }

    /**
     * Fetches a configuration string for the given configuration for the specified job. The job may
     * be specified via job key or class name, but the usage must be consistent.
     *
     * @param jobKey
     *  the key or class name of the job for which to build the configuration string
     *
     * @return
     *  the configuration string for the given configuration for the specified job
     */
    public static String jobConfig(String jobKey, String cfgName) {
        StringBuilder builder = new StringBuilder(ASYNC_JOBS_PREFIX)
            .append(jobKey)
            .append('.')
            .append(cfgName);

        return builder.toString();
    }

    public static final String ENTITLER_BULK_SIZE = "entitler.bulk.size";

    public static final Map<String, String> DEFAULT_PROPERTIES = new HashMap<>() {
        private static final long serialVersionUID = 1L;

        {
            this.put(CANDLEPIN_URL, "https://localhost");

            this.put(JWT_ISSUER, "Candlepin");
            this.put(JWT_TOKEN_TTL, "600"); // seconds
            this.put(ANON_JWT_TOKEN_TTL, "172800"); // seconds

            this.put(CA_KEY, "/etc/candlepin/certs/candlepin-ca.key");
            this.put(CA_CERT, "/etc/candlepin/certs/candlepin-ca.crt");
            this.put(CA_CERT_UPSTREAM, "/etc/candlepin/certs/upstream");

            this.put(ACTIVATION_DEBUG_PREFIX, "");

            this.put(CPM_PROVIDER, "artemis");

            this.put(ACTIVEMQ_ENABLED, "true");
            this.put(ACTIVEMQ_EMBEDDED, "true");

            // TODO: Delete the above configuration and only use EMBEDDED_BROKER going forward.
            // This is currently blocked by complexity in supporting moving configuration paths,
            // and should coincide with an update to the Configuration object
            this.put(ACTIVEMQ_EMBEDDED_BROKER, "true");
            this.put(ACTIVEMQ_JAAS_INVM_LOGIN_NAME, "InVMLogin");
            this.put(ACTIVEMQ_JAAS_CERTIFICATE_LOGIN_NAME, "CertificateLogin");

            // By default, connect to embedded artemis (InVM)
            this.put(ACTIVEMQ_BROKER_URL, "vm://0");

            // By default use the broker.xml file that is packaged in the war.
            this.put(ACTIVEMQ_SERVER_CONFIG_PATH, "");
            this.put(ACTIVEMQ_LARGE_MSG_SIZE, Integer.toString(100 * 1024));
            this.put(ACTIVEMQ_CONNECTION_MONITOR_INTERVAL, "5000"); // milliseconds

            this.put(AUDIT_LISTENERS,
                "org.candlepin.audit.LoggingListener," +
                    "org.candlepin.audit.ActivationListener");
            this.put(AUDIT_FILTER_ENABLED, "false");

            this.put(ENTITLER_BULK_SIZE, "1000");

            // These default DO_NOT_FILTER events are those events needed by other Satellite components.
            this.put(AUDIT_FILTER_DO_NOT_FILTER,
                "CREATED-ENTITLEMENT," +
                    "DELETED-ENTITLEMENT," +
                    "CREATED-POOL," +
                    "DELETED-POOL," +
                    "CREATED-COMPLIANCE");

            this.put(AUDIT_FILTER_DO_FILTER, "");
            this.put(AUDIT_FILTER_DEFAULT_POLICY, "DO_FILTER");

            this.put(PRETTY_PRINT, "false");

            this.put(SYNC_WORK_DIR, "/var/cache/candlepin/sync");
            this.put(CONSUMER_FACTS_MATCHER, ".*");
            this.put(TRUSTED_AUTHENTICATION, "false");
            this.put(SSL_AUTHENTICATION, "true");
            this.put(OAUTH_AUTHENTICATION, "false");
            this.put(KEYCLOAK_AUTHENTICATION, "false");
            this.put(BASIC_AUTHENTICATION, "true");
            this.put(CLOUD_AUTHENTICATION, "false");
            this.put(ACTIVATION_KEY_AUTHENTICATION, "true");

            this.put(AUTH_OVER_HTTP, "false");
            // By default, environments should be hidden so clients do not need to
            // submit one when registering.
            this.put(HIDDEN_RESOURCES, "environments");
            this.put(HIDDEN_CAPABILITIES, "");
            this.put(DB_MANAGE_ON_START, CandlepinContextListener.DBManagementLevel.NONE.getName());
            this.put(SSL_VERIFY, "false");

            this.put(FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false");

            this.put(CACHE_JMX_STATS, "false");
            this.put(CACHE_CONFIG_FILE_URI, "ehcache.xml");

            this.put(SUSPEND_MODE_ENABLED, "true");

            this.put(IDENTITY_CERT_YEAR_ADDENDUM, "5");
            this.put(IDENTITY_CERT_EXPIRY_THRESHOLD, "90");
            this.put(SHARD_WEBAPP, "candlepin");

            // defaults
            this.put(SHARD_USERNAME, "admin");
            this.put(SHARD_PASSWORD, "admin");
            this.put(STANDALONE, "true");

            this.put(ENV_CONTENT_FILTERING, "true");
            this.put(USE_SYSTEM_UUID_FOR_MATCHING, "true");

            // what constitutes a valid consumer name
            this.put(CONSUMER_SYSTEM_NAME_PATTERN, "[\\#\\?\\'\\`\\!@{}()\\[\\]\\?&\\w-\\.]+");
            this.put(CONSUMER_PERSON_NAME_PATTERN, "[\\#\\?\\'\\`\\!@{}()\\[\\]\\?&\\w-\\.]+");

            this.put(PREFIX_WEBURL, "localhost:8443/candlepin");
            this.put(PREFIX_APIURL, "localhost:8443/candlepin");
            this.put(PASSPHRASE_SECRET_FILE, "/etc/katello/secure/passphrase");
            this.put(KEYCLOAK_FILEPATH, "/etc/candlepin/keycloak.json");

            /**
             *  Defines the maximum number of products allowed in the product cache.
             *  On deployments with a large number of products, it might be better
             *  to set this to a large number, keeping in mind that it will yield
             *  a larger memory footprint as the cache fills up.
             */
            this.put(PRODUCT_CACHE_MAX, "100");

            /** As we do math on some facts and attributes, we need to constrain some values */
            this.put(INTEGER_FACTS, INTEGER_FACT_LIST);
            this.put(NON_NEG_INTEGER_FACTS, NON_NEG_INTEGER_FACT_LIST);
            this.put(INTEGER_ATTRIBUTES, INTEGER_ATTRIBUTE_LIST);
            this.put(NON_NEG_INTEGER_ATTRIBUTES, NON_NEG_INTEGER_ATTRIBUTE_LIST);
            this.put(LONG_ATTRIBUTES, LONG_ATTRIBUTE_LIST);
            this.put(NON_NEG_LONG_ATTRIBUTES, NON_NEG_LONG_ATTRIBUTE_LIST);
            this.put(BOOLEAN_ATTRIBUTES, BOOLEAN_ATTRIBUTE_LIST);

            this.put(SWAGGER_ENABLED, Boolean.toString(true));
            this.put(TOKENPAGE_ENABLED, Boolean.toString(true));

            // Async job defaults and scheduling
            // Quartz scheduling bits
            this.put("org.quartz.scheduler.skipUpdateCheck", "true");
            this.put("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
            this.put("org.quartz.threadPool.threadCount", "15");
            this.put("org.quartz.threadPool.threadPriority", "5");

            this.put(ASYNC_JOBS_THREADS, "10");
            this.put(ASYNC_JOBS_QUEUE_WHILE_SUSPENDED, "true");
            this.put(ASYNC_JOBS_SCHEDULER_ENABLED, "true");
            this.put(ASYNC_JOBS_THREAD_SHUTDOWN_TIMEOUT, "600"); // 10 minutes

            this.put(ASYNC_JOBS_DISPATCH_ADDRESS, "job");
            this.put(ASYNC_JOBS_RECEIVE_ADDRESS, "jobs");
            this.put(ASYNC_JOBS_RECEIVE_FILTER, "");

            // ActiveEntitlementJob
            this.put(jobConfig(ActiveEntitlementJob.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                ActiveEntitlementJob.DEFAULT_SCHEDULE);

            // CertificateCleanupJob
            this.put(jobConfig(CertificateCleanupJob.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                CertificateCleanupJob.DEFAULT_SCHEDULE);

            // EntitlerJob
            this.put(jobConfig(EntitlerJob.JOB_KEY, EntitlerJob.CFG_JOB_THROTTLE),
                EntitlerJob.DEFAULT_THROTTLE);

            // ExpiredPoolsCleanupJob
            this.put(jobConfig(ExpiredPoolsCleanupJob.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                ExpiredPoolsCleanupJob.DEFAULT_SCHEDULE);

            // ImportRecordCleanerJob
            this.put(jobConfig(ImportRecordCleanerJob.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                ImportRecordCleanerJob.DEFAULT_SCHEDULE);
            this.put(jobConfig(ImportRecordCleanerJob.JOB_KEY, ImportRecordCleanerJob.CFG_KEEP),
                ImportRecordCleanerJob.DEFAULT_KEEP);

            // InactiveConsumeCleanerJob
            this.put(jobConfig(InactiveConsumerCleanerJob.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                ConfigProperties.ASYNC_JOBS_MANUAL_SCHEDULE);
            this.put(jobConfig(InactiveConsumerCleanerJob.JOB_KEY, InactiveConsumerCleanerJob.CFG_BATCH_SIZE),
                InactiveConsumerCleanerJob.DEFAULT_BATCH_SIZE);
            this.put(jobConfig(InactiveConsumerCleanerJob.JOB_KEY,
                InactiveConsumerCleanerJob.CFG_LAST_CHECKED_IN_RETENTION_IN_DAYS),
                Integer.toString(InactiveConsumerCleanerJob.DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS));
            this.put(jobConfig(InactiveConsumerCleanerJob.JOB_KEY,
                InactiveConsumerCleanerJob.CFG_LAST_UPDATED_IN_RETENTION_IN_DAYS),
                Integer.toString(InactiveConsumerCleanerJob.DEFAULT_LAST_UPDATED_IN_RETENTION_IN_DAYS));

            // JobCleaner
            this.put(jobConfig(JobCleaner.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                JobCleaner.DEFAULT_SCHEDULE);
            this.put(jobConfig(JobCleaner.JOB_KEY, JobCleaner.CFG_MAX_TERMINAL_JOB_AGE),
                JobCleaner.DEFAULT_MAX_TERMINAL_AGE);
            this.put(jobConfig(JobCleaner.JOB_KEY, JobCleaner.CFG_MAX_NONTERMINAL_JOB_AGE),
                JobCleaner.DEFAULT_MAX_NONTERMINAL_AGE);
            this.put(jobConfig(JobCleaner.JOB_KEY, JobCleaner.CFG_MAX_RUNNING_JOB_AGE),
                JobCleaner.DEFAULT_MAX_RUNNING_AGE);

            // ManifestCleanerJob
            this.put(jobConfig(ManifestCleanerJob.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                ManifestCleanerJob.DEFAULT_SCHEDULE);
            this.put(jobConfig(ManifestCleanerJob.JOB_KEY, ManifestCleanerJob.CFG_MAX_AGE_IN_MINUTES),
                Integer.toString(ManifestCleanerJob.DEFAULT_MAX_AGE_IN_MINUTES));

            // OrphanCleanupJob
            this.put(jobConfig(OrphanCleanupJob.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                OrphanCleanupJob.DEFAULT_SCHEDULE);

            // UnmappedGuestEntitlementCleanerJob
            this.put(jobConfig(UnmappedGuestEntitlementCleanerJob.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                UnmappedGuestEntitlementCleanerJob.DEFAULT_SCHEDULE);

            // Set the triggerable jobs list
            this.put(ASYNC_JOBS_TRIGGERABLE_JOBS, String.join(", ", ASYNC_JOBS_TRIGGERABLE_JOBS_LIST));

            this.put(ORPHANED_ENTITY_GRACE_PERIOD, "30");

            // Based on testing with the hypervisor check in process, and going a bit conservative
            this.put(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE, "15000");
            this.put(DatabaseConfigFactory.CASE_OPERATOR_BLOCK_SIZE, "100");
            this.put(DatabaseConfigFactory.BATCH_BLOCK_SIZE, "500");
            this.put(DatabaseConfigFactory.QUERY_PARAMETER_LIMIT, "32000");
        }
    };
}
