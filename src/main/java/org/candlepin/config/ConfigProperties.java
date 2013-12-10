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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.candlepin.pinsetter.tasks.CancelJobJob;
import org.candlepin.pinsetter.tasks.CertificateRevocationListTask;
import org.candlepin.pinsetter.tasks.ExpiredPoolsJob;
import org.candlepin.pinsetter.tasks.ExportCleaner;
import org.candlepin.pinsetter.tasks.ImportRecordJob;
import org.candlepin.pinsetter.tasks.JobCleaner;
import org.candlepin.pinsetter.tasks.SweepBarJob;
import org.candlepin.pinsetter.tasks.UnpauseJob;
import org.candlepin.pinsetter.tasks.StatisticHistoryTask;

/**
 * Defines a map of default properties used to prepopulate the {@link Config}.
 * Also holds static keys for config lookup.
 */
public class ConfigProperties {
    private ConfigProperties() {
    }

    public static final String CANDLEPIN_URL = "candlepin.url";

    public static final String CA_KEY = "candlepin.ca_key";
    public static final String CA_CERT = "candlepin.ca_cert";
    public static final String FAIL_ON_UNKNOWN_IMPORT_PROPERTIES =
        "candlepin.importer.fail_on_unknown";
    public static final String CA_CERT_UPSTREAM = "candlepin.upstream_ca_cert";
    public static final String CA_KEY_PASSWORD = "candlepin.ca_key_password";

    public static final String HORNETQ_BASE_DIR = "candlepin.audit.hornetq.base_dir";
    public static final String HORNETQ_LARGE_MSG_SIZE =
                                      "candlepin.audit.hornetq.large_msg_size";
    public static final String AUDIT_LISTENERS = "candlepin.audit.listeners";
    public static final String AUDIT_LOG_FILE = "candlepin.audit.log_file";
    public static final String AUDIT_LOG_VERBOSE = "candlepin.audit.log_verbose";

    public static final String PRETTY_PRINT = "candlepin.pretty_print";
    public static final String REVOKE_ENTITLEMENT_IN_FIFO_ORDER =
                                      "candlepin.entitlement.revoke.order.fifo";
    public static final String ACTIVATION_DEBUG_PREFIX =
                                      "candlepin.subscription.activation.debug_prefix";

    // Space separated list of resources to hide in the GET / list:
    public static final String HIDDEN_RESOURCES = "candlepin.hidden_resources";

    // Authentication
    public static final String TRUSTED_AUTHENTICATION = "candlepin.auth.trusted.enable";
    public static final String SSL_AUTHENTICATION = "candlepin.auth.ssl.enable";
    public static final String OAUTH_AUTHENTICATION = "candlepin.auth.oauth.enable";
    public static final String BASIC_AUTHENTICATION = "candlepin.auth.basic.enable";

    // Pinsetter
    public static final String TASKS = "pinsetter.tasks";
    public static final String DEFAULT_TASKS = "pinsetter.default_tasks";
    public static final String ENABLE_PINSETTER = "candlepin.pinsetter.enable";

    private static final String[] DEFAULT_TASK_LIST = new String[]{
        CertificateRevocationListTask.class.getName(),
        JobCleaner.class.getName(), ImportRecordJob.class.getName(),
        StatisticHistoryTask.class.getName(),
        CancelJobJob.class.getName(), ExpiredPoolsJob.class.getName(),
        UnpauseJob.class.getName(), SweepBarJob.class.getName(),
        ExportCleaner.class.getName()};

    public static final String SYNC_WORK_DIR = "candlepin.sync.work_dir";
    public static final String CONSUMER_FACTS_MATCHER =
                                  "candlepin.consumer.facts.match_regex";

    public static final String SHARD_USERNAME = "candlepin.shard.username";
    public static final String SHARD_PASSWORD = "candlepin.shard.password";
    public static final String SHARD_WEBAPP = "candlepin.shard.webapp";

    public static final String STANDALONE = "candlepin.standalone";
    public static final String ENV_CONTENT_FILTERING =
        "candlepin.environment_content_filtering";

    public static final String CONSUMER_SYSTEM_NAME_PATTERN =
         "candlepin.consumer_system_name_pattern";
    public static final String CONSUMER_PERSON_NAME_PATTERN =
         "candlepin.consumer_person_name_pattern";

    public static final String PREFIX_WEBURL = "candlepin.export.prefix.weburl";
    public static final String PREFIX_APIURL = "candlepin.export.prefix.apiurl";
    public static final String PASSPHRASE_SECRET_FILE =
        "candlepin.passphrase.path";

    public static final String PRODUCT_CACHE_MAX = "candlepin.cache.product_cache_max";

    public static final String INTEGER_FACTS =
        "candlepin.integer_facts";
    private static final String INTEGER_FACT_LIST =
        "";

    public static final String NON_NEG_INTEGER_FACTS =
        "candlepin.positive_integer_facts";
    private static final String NON_NEG_INTEGER_FACT_LIST =
        "cpu.core(s)_per_socket," +
        "cpu.cpu(s)," +
        "cpu.cpu_socket(s)," +
        "lscpu.core(s)_per_socket," +
        "lscpu.cpu(s)," +
        "lscpu.numa_node(s)," +
        "lscpu.socket(s)," +
        "lscpu.thread(s)_per_core";

    public static final String INTEGER_ATTRIBUTES =
        "candlepin.integer_attributes";
    private static final String INTEGER_ATTRIBUTE_LIST = "";

    public static final String NON_NEG_INTEGER_ATTRIBUTES =
        "candlepin.positive_integer_attributes";
    private static final String NON_NEG_INTEGER_ATTRIBUTE_LIST =
        "sockets," +
        "warning_period," +
        "ram," +
        "cores";

    public static final String LONG_ATTRIBUTES =
        "candlepin.long_attributes";
    private static final String LONG_ATTRIBUTE_LIST =
        "";

    public static final String NON_NEG_LONG_ATTRIBUTES =
        "candlepin.positive_long_attributes";
    private static final String NON_NEG_LONG_ATTRIBUTE_LIST =
        "metadata_expire";

    public static final String BOOLEAN_ATTRIBUTES = "candlepin.boolean_attributes";
    private static final String BOOLEAN_ATTRIBUTE_LIST =
        "management_enabled," +
        "virt_only";

    public static final Map<String, String> DEFAULT_PROPERTIES =
        new HashMap<String, String>() {

            private static final long serialVersionUID = 1L;
            {
                this.put(CANDLEPIN_URL, "https://localhost");

                this.put(CA_KEY, "/etc/candlepin/certs/candlepin-ca.key");
                this.put(CA_CERT, "/etc/candlepin/certs/candlepin-ca.crt");
                this.put(CA_CERT_UPSTREAM, "/etc/candlepin/certs/upstream");

                this.put(ACTIVATION_DEBUG_PREFIX, "");

                this.put(HORNETQ_BASE_DIR, "/var/lib/candlepin/hornetq");
                this.put(HORNETQ_LARGE_MSG_SIZE, Integer.toString(100 * 1024));
                this.put(AUDIT_LISTENERS,
                    "org.candlepin.audit.DatabaseListener," +
                        "org.candlepin.audit.LoggingListener," +
                        "org.candlepin.audit.ActivationListener");
                this.put(AUDIT_LOG_FILE, "/var/log/candlepin/audit.log");
                this.put(AUDIT_LOG_VERBOSE, "false");

                this.put(PRETTY_PRINT, "false");
                this.put(REVOKE_ENTITLEMENT_IN_FIFO_ORDER, "true");
                this.put(CRL_FILE_PATH, "/var/lib/candlepin/candlepin-crl.crl");

                this.put(SYNC_WORK_DIR, "/var/cache/candlepin/sync");
                this.put(CONSUMER_FACTS_MATCHER, ".*");
                this.put(TRUSTED_AUTHENTICATION, "false");
                this.put(SSL_AUTHENTICATION, "true");
                this.put(OAUTH_AUTHENTICATION, "false");
                this.put(BASIC_AUTHENTICATION, "true");

                // By default, environments should be hidden so clients do not need to
                // submit one when registering.
                this.put(HIDDEN_RESOURCES, "environments");

                this.put(FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false");

                // Pinsetter
                // prevent Quartz from checking for updates
                this.put("org.quartz.scheduler.skipUpdateCheck", "true");
                this.put("org.quartz.threadPool.class",
                    "org.quartz.simpl.SimpleThreadPool");
                this.put("org.quartz.threadPool.threadCount", "15");
                this.put("org.quartz.threadPool.threadPriority", "5");
                this.put(DEFAULT_TASKS, StringUtils.join(DEFAULT_TASK_LIST, ","));

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
                this.put(CONSUMER_SYSTEM_NAME_PATTERN,
                    "[\\#\\?\\'\\`\\!@{}()\\[\\]\\?&\\w-\\.]+");
                this.put(CONSUMER_PERSON_NAME_PATTERN,
                    "[\\#\\?\\'\\`\\!@{}()\\[\\]\\?&\\w-\\.]+");

                this.put(PREFIX_WEBURL, "localhost:8443/candlepin");
                this.put(PREFIX_APIURL, "localhost:8443/candlepin");
                this.put(PASSPHRASE_SECRET_FILE,
                    "/etc/katello/secure/passphrase");

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
            }
        };
    public static final String CRL_FILE_PATH = "candlepin.crl.file";
    public static final String IDENTITY_CERT_YEAR_ADDENDUM =
                               "candlepin.identityCert.yr.addendum";
    /**
     * Identity certificate expiry threshold in days
     */
    public static final String IDENTITY_CERT_EXPIRY_THRESHOLD =
                               "candlepin.identityCert.expiry.threshold";

}
