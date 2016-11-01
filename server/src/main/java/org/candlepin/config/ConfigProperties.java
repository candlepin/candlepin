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

import static org.candlepin.common.config.ConfigurationPrefixes.JPA_CONFIG_PREFIX;

import org.candlepin.pinsetter.tasks.ActiveEntitlementJob;
import org.candlepin.pinsetter.tasks.CancelJobJob;
import org.candlepin.pinsetter.tasks.CertificateRevocationListTask;
import org.candlepin.pinsetter.tasks.EntitlerJob;
import org.candlepin.pinsetter.tasks.ExpiredPoolsJob;
import org.candlepin.pinsetter.tasks.ImportRecordJob;
import org.candlepin.pinsetter.tasks.JobCleaner;
import org.candlepin.pinsetter.tasks.ManifestCleanerJob;
import org.candlepin.pinsetter.tasks.SweepBarJob;
import org.candlepin.pinsetter.tasks.UnmappedGuestEntitlementCleanerJob;
import org.candlepin.pinsetter.tasks.UnpauseJob;

import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines a map of default properties used to prepopulate the {@link Config}.
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

    public static final String HORNETQ_ENABLED = "candlepin.audit.hornetq.enable";
    public static final String HORNETQ_BASE_DIR = "candlepin.audit.hornetq.base_dir";
    /**
     * This number is large message size. Any message
     * that is bigger than this number is regarded as large.
     * That means that HornetQ will page this message
     * to a disk. Setting this number to something high
     * e.g. 1 000 0000 will effectively disable the
     * functionality. This is handy when you expect a lot of
     * messages and you do not want to run out of disk space.
     */
    public static final String HORNETQ_LARGE_MSG_SIZE = "candlepin.audit.hornetq.large_msg_size";

    /**
     * Setting number of server threads that will be
     * created for Hornet. -1 means that default value
     * will be used.
     */
    public static final String HORNETQ_MAX_SCHEDULED_THREADS =
        "candlepin.audit.hornetq.max_scheduled_threads";
    public static final String HORNETQ_MAX_THREADS = "candlepin.audit.hornetq.max_threads";
    /**
     * This can be either BLOCK or PAGE. When set to PAGE then
     * Hornet will keep only up to HORNET_MAX_QUEUE_SIZE
     * kilobytes of queue messages in memory. Anything above
     * HORNET_MAX_QUEUE_SIZE will be paged to a hard disk.
     * When set to BLOCK then after reaching
     * HORNET_MAX_QUEUE_SIZE, all the producers will be blocked until
     * the queues are freed up by consumers. Thus, BLOCK
     * may be dangerous when consumers fail, because
     * producers will timeout.
     */
    public static final String HORNETQ_ADDRESS_FULL_POLICY = "candlepin.audit.hornetq.address_full_policy";
    public static final String HORNETQ_MAX_QUEUE_SIZE = "candlepin.audit.hornetq.max_queue_size";
    /**
     * This is applicable only for PAGE setting of HORNETQ_ADDRESS_FULL_POLICY.
     */
    public static final String HORNETQ_MAX_PAGE_SIZE = "candlepin.audit.hornetq.max_page_size";

    public static final String AUDIT_LISTENERS = "candlepin.audit.listeners";
    public static final String AUDIT_LOG_FILE = "candlepin.audit.log_file";
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
     * will be filtered, meaning they will not enter HORNETQ.
     */
    public static final String AUDIT_FILTER_DEFAULT_POLICY = "candlepin.audit.filter.policy";
    public static final String AUDIT_LOG_VERBOSE = "candlepin.audit.log_verbose";

    public static final String PRETTY_PRINT = "candlepin.pretty_print";
    public static final String REVOKE_ENTITLEMENT_IN_FIFO_ORDER = "candlepin.entitlement.revoke.order.fifo";
    public static final String ACTIVATION_DEBUG_PREFIX = "candlepin.subscription.activation.debug_prefix";

    // Space separated list of resources to hide in the GET / list:
    public static final String HIDDEN_RESOURCES = "candlepin.hidden_resources";

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
        CertificateRevocationListTask.class.getName(),
        JobCleaner.class.getName(),
        ImportRecordJob.class.getName(),
        ManifestCleanerJob.class.getName(), ActiveEntitlementJob.class.getName(),
        CancelJobJob.class.getName(),
        ExpiredPoolsJob.class.getName(),
        UnpauseJob.class.getName(),
        SweepBarJob.class.getName(),
        ActiveEntitlementJob.class.getName(),
        UnmappedGuestEntitlementCleanerJob.class.getName(),
    };

    public static final String MANIFEST_CLEANER_JOB_MAX_AGE_IN_MINUTES =
        "pinsetter.org.candlepin.pinsetter.tasks.ManifestCleanerJob.max_age_in_minutes";

    public static final String ENTITLER_JOB_THROTTLE =
        "pinsetter." + EntitlerJob.class.getName() + ".throttle";

    public static final String BATCH_BIND_NUMBER_OF_POOLS_LIMIT =
        "candlepin.batch.bind.number_of_pools_limit";

    public static final String SYNC_WORK_DIR = "candlepin.sync.work_dir";

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

    public static final String IDENTITY_CERT_YEAR_ADDENDUM = "candlepin.identityCert.yr.addendum";
    /**
     * Identity certificate expiry threshold in days
     */
    public static final String IDENTITY_CERT_EXPIRY_THRESHOLD = "candlepin.identityCert.expiry.threshold";

    public static final String SWAGGER_ENABLED = "candlepin.swagger.enabled";

    public static final Map<String, String> DEFAULT_PROPERTIES = new HashMap<String, String>() {
        private static final long serialVersionUID = 1L;

        {
            this.put(CANDLEPIN_URL, "https://localhost");

            this.put(CA_KEY, "/etc/candlepin/certs/candlepin-ca.key");
            this.put(CA_CERT, "/etc/candlepin/certs/candlepin-ca.crt");
            this.put(CA_CERT_UPSTREAM, "/etc/candlepin/certs/upstream");

            this.put(ACTIVATION_DEBUG_PREFIX, "");

            this.put(HORNETQ_ENABLED, "true");
            this.put(HORNETQ_BASE_DIR, "/var/lib/candlepin/hornetq");
            this.put(HORNETQ_LARGE_MSG_SIZE, Integer.toString(100 * 1024));

            this.put(HORNETQ_MAX_THREADS, "-1");
            this.put(HORNETQ_MAX_SCHEDULED_THREADS, "-1");
            this.put(HORNETQ_ADDRESS_FULL_POLICY, "PAGE");
            this.put(HORNETQ_MAX_QUEUE_SIZE, "10");
            this.put(HORNETQ_MAX_PAGE_SIZE, "1");
            this.put(AUDIT_LISTENERS,
                "org.candlepin.audit.DatabaseListener," +
                "org.candlepin.audit.LoggingListener," +
                "org.candlepin.audit.ActivationListener");
            this.put(AUDIT_LOG_FILE, "/var/log/candlepin/audit.log");
            this.put(AUDIT_LOG_VERBOSE, "false");
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
            this.put(REVOKE_ENTITLEMENT_IN_FIFO_ORDER, "true");
            this.put(CRL_FILE_PATH, "/var/lib/candlepin/candlepin-crl.crl");
            this.put(CRL_NEXT_UPDATE_DELTA, "1");

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

            this.put(AMQP_CONNECTION_RETRY_INTERVAL, "10"); // Every 10 seconds
            this.put(AMQP_CONNECTION_RETRY_ATTEMPTS, "12"); // Try for 2 mins (10s * 12)

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

            /**
             * As we do math on some facts and attributes, we need to constrain
             * some values
             */
            this.put(INTEGER_FACTS, INTEGER_FACT_LIST);
            this.put(NON_NEG_INTEGER_FACTS, NON_NEG_INTEGER_FACT_LIST);
            this.put(INTEGER_ATTRIBUTES, INTEGER_ATTRIBUTE_LIST);
            this.put(NON_NEG_INTEGER_ATTRIBUTES, NON_NEG_INTEGER_ATTRIBUTE_LIST);
            this.put(LONG_ATTRIBUTES, LONG_ATTRIBUTE_LIST);
            this.put(NON_NEG_LONG_ATTRIBUTES, NON_NEG_LONG_ATTRIBUTE_LIST);
            this.put(BOOLEAN_ATTRIBUTES, BOOLEAN_ATTRIBUTE_LIST);

            // Default 20 minutes
            this.put(PINSETTER_ASYNC_JOB_TIMEOUT, Integer.toString(1200));
            this.put(PINSETTER_MAX_RETRIES, Integer.toString(PINSETTER_MAX_RETRIES_DEFAULT));
            this.put(SWAGGER_ENABLED, Boolean.toString(true));

            // ManifestCleanerJob config
            // Max Age: 24 hours
            this.put(MANIFEST_CLEANER_JOB_MAX_AGE_IN_MINUTES, "1440");
        }
    };

}
