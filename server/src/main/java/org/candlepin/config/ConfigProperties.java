/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import static org.candlepin.common.config.ConfigurationPrefixes.*;

import org.candlepin.async.tasks.ActiveEntitlementJob;
import org.candlepin.async.tasks.JobCleaner;
import org.candlepin.async.tasks.CRLUpdateJob;
import org.candlepin.async.tasks.ExpiredPoolsCleanupJob;
import org.candlepin.async.tasks.ImportRecordCleanerJob;
import org.candlepin.async.tasks.ManifestCleanerJob;
import org.candlepin.async.tasks.OrphanCleanupJob;
import org.candlepin.async.tasks.UnmappedGuestEntitlementCleanerJob;
import org.candlepin.common.config.Configuration;
import org.candlepin.pinsetter.tasks.CancelJobJob;
import org.candlepin.pinsetter.tasks.EntitlerJob;
import org.candlepin.pinsetter.tasks.SweepBarJob;
import org.candlepin.pinsetter.tasks.UnpauseJob;

import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;



/**
 * Defines a map of default properties used to prepopulate the {@link Configuration}.
 * Also holds static keys for config lookup.
 */
public class ConfigProperties {
    private ConfigProperties() {
    }

    public static final String DEFAULT_CONFIG_FILE = "/etc/candlepin/candlepin.conf";

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
     * XXX The actual property key refers to HornetQ which was ActiveMQ's ancestor.  We have to keep the
     * key unchanged for compatibility reasons.
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
     */
    public static final String ACTIVEMQ_LARGE_MSG_SIZE = "candlepin.audit.hornetq.large_msg_size";

    /**
     * The interval (in milliseconds) at which the ActiveMQStatusMonitor will check the connection state
     * once the connection has dropped.
     */
    public static final String ACTIVEMQ_CONNECTION_MONITOR_INTERVAL =
        "candlepin.audit.hornetq.monitor.interval";

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

    // Authentication
    public static final String TRUSTED_AUTHENTICATION = "candlepin.auth.trusted.enable";
    public static final String SSL_AUTHENTICATION = "candlepin.auth.ssl.enable";
    public static final String OAUTH_AUTHENTICATION = "candlepin.auth.oauth.enable";
    public static final String BASIC_AUTHENTICATION = "candlepin.auth.basic.enable";

    // AMQP stuff
    public static final String AMQP_INTEGRATION_ENABLED = "candlepin.amqp.enable";
    public static final String AMQP_CONNECT_STRING = "candlepin.amqp.connect";
    public static final String AMQP_KEYSTORE = "candlepin.amqp.keystore";
    public static final String AMQP_KEYSTORE_PASSWORD = "candlepin.amqp.keystore_password";
    public static final String AMQP_TRUSTSTORE = "candlepin.amqp.truststore";
    public static final String AMQP_TRUSTSTORE_PASSWORD = "candlepin.amqp.truststore_password";
    public static final String AMQP_CONNECTION_RETRY_ATTEMPTS = "gutterball.amqp.connection.retry_attempts";
    public static final String AMQP_CONNECTION_RETRY_INTERVAL = "gutterball.amqp.connection.retry_interval";

    // Quartz configurations
    public static final String QUARTZ_CLUSTERED_MODE = "org.quartz.jobStore.isClustered";

    /**
     * A possibility to enable Suspend Mode. By default, the suspend mode is enabled
     */
    public static final String SUSPEND_MODE_ENABLED = "candlepin.suspend_mode_enabled";
    /**
     * Timeout that is used for QpidQmf while receiving messages. It shouldn't be necessary
     * to modify this unless the environment and Qpid Broker is so heavily utilized, that
     * reception takes longer.
     */
    public static final String QPID_QMF_RECEIVE_TIMEOUT = "candlepin.amqp.qmf.receive_timeout";

    /**
     * Period to wait between attempts to reconnect to Qpid.
     */
    public static final String QPID_MODE_TRANSITIONER_DELAY = "candlepin.amqp.suspend.transitioner_delay";

    // Hibernate
    public static final String DB_PASSWORD = JPA_CONFIG_PREFIX + "hibernate.connection.password";
    // Cache
    public static final String CACHE_JMX_STATS = "cache.jmx.statistics";

    public static final String[] ENCRYPTED_PROPERTIES = new String[] {
        DB_PASSWORD,
    };

    // Pinsetter
    public static final String TASKS = "pinsetter.tasks";
    public static final String DEFAULT_TASKS = "pinsetter.default_tasks";
    public static final String ENABLE_PINSETTER = "candlepin.pinsetter.enable";
    public static final String PINSETTER_ASYNC_JOB_TIMEOUT = "pinsetter.waiting.timeout.seconds";
    public static final String PINSETTER_MAX_RETRIES = "pinsetter.retries.max";
    public static final int PINSETTER_MAX_RETRIES_DEFAULT = 10;

    public static final String[] DEFAULT_TASK_LIST = new String[] {
        CancelJobJob.class.getName(),
        SweepBarJob.class.getName(),
        UnpauseJob.class.getName(),
    };

    public static final String MANIFEST_CLEANER_JOB_MAX_AGE_IN_MINUTES =
        "pinsetter.org.candlepin.pinsetter.tasks.ManifestCleanerJob.max_age_in_minutes";

    public static final String ENTITLER_JOB_THROTTLE =
        "pinsetter." + EntitlerJob.class.getName() + ".throttle";

    public static final String BATCH_BIND_NUMBER_OF_POOLS_LIMIT =
        "candlepin.batch.bind.number_of_pools_limit";

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

    public static final String CONSUMER_SYSTEM_NAME_PATTERN = "candlepin.consumer_system_name_pattern";
    public static final String CONSUMER_PERSON_NAME_PATTERN = "candlepin.consumer_person_name_pattern";

    public static final String PREFIX_WEBURL = "candlepin.export.prefix.weburl";
    public static final String PREFIX_APIURL = "candlepin.export.prefix.apiurl";
    public static final String PASSPHRASE_SECRET_FILE = "candlepin.passphrase.path";

    public static final String PRODUCT_CACHE_MAX = "candlepin.cache.product_cache_max";

    public static final String INTEGER_FACTS = "candlepin.integer_facts";
    private static final String INTEGER_FACT_LIST = "";

    public static final String NON_NEG_INTEGER_FACTS = "candlepin.positive_integer_facts";
    private static final String NON_NEG_INTEGER_FACT_LIST =
        "cpu.core(s)_per_socket," +
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
    private static final String NON_NEG_INTEGER_ATTRIBUTE_LIST =
        "sockets," +
        "warning_period," +
        "ram," +
        "cores";

    public static final String LONG_ATTRIBUTES = "candlepin.long_attributes";
    private static final String LONG_ATTRIBUTE_LIST = "";

    public static final String NON_NEG_LONG_ATTRIBUTES = "candlepin.positive_long_attributes";
    private static final String NON_NEG_LONG_ATTRIBUTE_LIST = "metadata_expire";

    public static final String BOOLEAN_ATTRIBUTES = "candlepin.boolean_attributes";
    private static final String BOOLEAN_ATTRIBUTE_LIST =
        "management_enabled," +
        "virt_only";

    /**
     * The number of days from today to set the nextUpdate date when generating the CRL.
     * See http://security.stackexchange.com/a/55784
     */
    public static final String CRL_NEXT_UPDATE_DELTA = "candlepin.crl.nextupdate.delta_days";
    public static final String CRL_FILE_PATH = "candlepin.crl.file";

    /**
     * The number of uncollected and expired cert serials will be processed at a time while updating
     * the CRL file. It is important to note that both uncollected and expired serials will be batched
     * at the specified amount.
     *
     */
    public static final String CRL_SERIAL_BATCH_SIZE = "candlepin.crl.update_serial_batch_size";

    public static final String IDENTITY_CERT_YEAR_ADDENDUM = "candlepin.identityCert.yr.addendum";
    /**
     * Identity certificate expiry threshold in days
     */
    public static final String IDENTITY_CERT_EXPIRY_THRESHOLD = "candlepin.identityCert.expiry.threshold";

    public static final String SWAGGER_ENABLED = "candlepin.swagger.enabled";

    public static final String ASYNC_JOBS_THREADS = "candlepin.async.threads";
    public static final String ASYNC_JOBS_WHITELIST = "candlepin.async.whitelist";
    public static final String ASYNC_JOBS_BLACKLIST = "candlepin.async.blacklist";

    // Used for per-job configuration. The full syntax is "PREFIX.{job_key}.SUFFIX". For instance,
    // to configure the schedule flag for the job TestJob1, the full configuration would be:
    // candlepin.async.jobs.TestJob1.schedule=0 0 0/3 * * ?
    public static final String ASYNC_JOBS_PREFIX = "candlepin.async.jobs.";
    public static final String ASYNC_JOBS_JOB_ENABLED = "enabled";
    public static final String ASYNC_JOBS_JOB_SCHEDULE = "schedule";

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

    public static final Map<String, String> DEFAULT_PROPERTIES = new HashMap<String, String>() {
        private static final long serialVersionUID = 1L;

        {
            this.put(CANDLEPIN_URL, "https://localhost");

            this.put(CA_KEY, "/etc/candlepin/certs/candlepin-ca.key");
            this.put(CA_CERT, "/etc/candlepin/certs/candlepin-ca.crt");
            this.put(CA_CERT_UPSTREAM, "/etc/candlepin/certs/upstream");

            this.put(ACTIVATION_DEBUG_PREFIX, "");

            this.put(ACTIVEMQ_ENABLED, "true");
            this.put(ACTIVEMQ_EMBEDDED, "true");
            // By default, connect to embedded artemis (InVM)
            this.put(ACTIVEMQ_BROKER_URL, "vm://0");
            // By default use the broker.xml file that is packaged in the war.
            this.put(ACTIVEMQ_SERVER_CONFIG_PATH, "");
            this.put(ACTIVEMQ_LARGE_MSG_SIZE, Integer.toString(100 * 1024));
            this.put(ACTIVEMQ_CONNECTION_MONITOR_INTERVAL, "5000"); // milliseconds

            this.put(AUDIT_LISTENERS,
                // "org.candlepin.audit.DatabaseListener," +
                "org.candlepin.audit.LoggingListener," +
                "org.candlepin.audit.ActivationListener");
            this.put(AUDIT_FILTER_ENABLED, "false");

            /**
            * These default DO_NOT_FILTER events are those events needed by
            * other Satellite components. See sources:
            *
            * https://gitlab.sat.lab.tlv.redhat.com/satellite6/katello/blob/
            * SATELLITE-6.1.0/app/lib/actions/candlepin/reindex_pool_subscription_handler.rb#L43
            */
            this.put(AUDIT_FILTER_DO_NOT_FILTER,
                "CREATED-ENTITLEMENT," +
                "DELETED-ENTITLEMENT," +
                "CREATED-POOL," +
                "DELETED-POOL," +
                "CREATED-COMPLIANCE");

            this.put(AUDIT_FILTER_DO_FILTER, "");
            this.put(AUDIT_FILTER_DEFAULT_POLICY, "DO_FILTER");

            this.put(PRETTY_PRINT, "false");
            this.put(CRL_FILE_PATH, "/var/lib/candlepin/candlepin-crl.crl");
            this.put(CRL_NEXT_UPDATE_DELTA, "1");
            this.put(CRL_SERIAL_BATCH_SIZE, "1000000");

            this.put(SYNC_WORK_DIR, "/var/cache/candlepin/sync");
            this.put(CONSUMER_FACTS_MATCHER, ".*");
            this.put(TRUSTED_AUTHENTICATION, "false");
            this.put(SSL_AUTHENTICATION, "true");
            this.put(OAUTH_AUTHENTICATION, "false");
            this.put(BASIC_AUTHENTICATION, "true");
            this.put(AUTH_OVER_HTTP, "false");
            // By default, environments should be hidden so clients do not need to
            // submit one when registering.
            this.put(HIDDEN_RESOURCES, "environments");
            this.put(HIDDEN_CAPABILITIES, "");

            this.put(FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false");

            this.put(CACHE_JMX_STATS, "false");

            // Pinsetter
            // prevent Quartz from checking for updates
            this.put("org.quartz.scheduler.skipUpdateCheck", "true");
            this.put("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
            this.put("org.quartz.threadPool.threadCount", "15");
            this.put("org.quartz.threadPool.threadPriority", "5");
            this.put(DEFAULT_TASKS, StringUtils.join(DEFAULT_TASK_LIST, ","));
            this.put(ENTITLER_JOB_THROTTLE, "7");
            this.put(BATCH_BIND_NUMBER_OF_POOLS_LIMIT, "100");

            // AMQP (Qpid) configuration used by events
            this.put(AMQP_INTEGRATION_ENABLED, String.valueOf(false));
            this.put(AMQP_CONNECT_STRING, "tcp://localhost:5671?ssl='true'&ssl_cert_alias='candlepin'");
            this.put(AMQP_KEYSTORE, "/etc/candlepin/certs/amqp/candlepin.jks");
            this.put(AMQP_KEYSTORE_PASSWORD, "password");
            this.put(AMQP_TRUSTSTORE, "/etc/candlepin/certs/amqp/candlepin.truststore");
            this.put(AMQP_TRUSTSTORE_PASSWORD, "password");
            this.put(SUSPEND_MODE_ENABLED, "true");
            this.put(QPID_QMF_RECEIVE_TIMEOUT, "5000");
            this.put(QPID_MODE_TRANSITIONER_DELAY, "10");

            this.put(AMQP_CONNECTION_RETRY_INTERVAL, "10"); // Every 10 seconds
            this.put(AMQP_CONNECTION_RETRY_ATTEMPTS, "1"); // Try for 10 seconds (1*10s)

            this.put(IDENTITY_CERT_YEAR_ADDENDUM, "16");
            this.put(IDENTITY_CERT_EXPIRY_THRESHOLD, "90");
            this.put(SHARD_WEBAPP, "candlepin");
            this.put(ENABLE_PINSETTER, "true");

            // defaults
            this.put(SHARD_USERNAME, "admin");
            this.put(SHARD_PASSWORD, "admin");
            this.put(STANDALONE, "true");

            this.put(ENV_CONTENT_FILTERING, "true");

            // what constitutes a valid consumer name
            this.put(CONSUMER_SYSTEM_NAME_PATTERN, "[\\#\\?\\'\\`\\!@{}()\\[\\]\\?&\\w-\\.]+");
            this.put(CONSUMER_PERSON_NAME_PATTERN, "[\\#\\?\\'\\`\\!@{}()\\[\\]\\?&\\w-\\.]+");

            this.put(PREFIX_WEBURL, "localhost:8443/candlepin");
            this.put(PREFIX_APIURL, "localhost:8443/candlepin");
            this.put(PASSPHRASE_SECRET_FILE, "/etc/katello/secure/passphrase");

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

            // Async job defaults and scheduling
            this.put(ASYNC_JOBS_THREADS, "10");

            this.put(jobConfig(ActiveEntitlementJob.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                ActiveEntitlementJob.DEFAULT_SCHEDULE);
            this.put(jobConfig(CRLUpdateJob.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                CRLUpdateJob.DEFAULT_SCHEDULE);
            this.put(jobConfig(ExpiredPoolsCleanupJob.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                ExpiredPoolsCleanupJob.DEFAULT_SCHEDULE);
            this.put(jobConfig(ImportRecordCleanerJob.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                ImportRecordCleanerJob.DEFAULT_SCHEDULE);
            this.put(jobConfig(JobCleaner.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                JobCleaner.DEFAULT_SCHEDULE);
            this.put(jobConfig(ManifestCleanerJob.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                ManifestCleanerJob.DEFAULT_SCHEDULE);
            this.put(jobConfig(OrphanCleanupJob.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                OrphanCleanupJob.DEFAULT_SCHEDULE);
            this.put(jobConfig(UnmappedGuestEntitlementCleanerJob.JOB_KEY, ASYNC_JOBS_JOB_SCHEDULE),
                UnmappedGuestEntitlementCleanerJob.DEFAULT_SCHEDULE);

            // Old Pinsetter job configs

            // Default 20 minutes
            this.put(PINSETTER_ASYNC_JOB_TIMEOUT, Integer.toString(1200));
            this.put(PINSETTER_MAX_RETRIES, Integer.toString(PINSETTER_MAX_RETRIES_DEFAULT));

            // ManifestCleanerJob config (note: this is for the Pinsetter version)
            // Max Age: 24 hours
            this.put(MANIFEST_CLEANER_JOB_MAX_AGE_IN_MINUTES, "1440");


        }
    };

}
