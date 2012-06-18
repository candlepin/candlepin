/**
 * Copyright (c) 2009 Red Hat, Inc.
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
import org.candlepin.pinsetter.tasks.ImportRecordJob;
import org.candlepin.pinsetter.tasks.JobCleaner;
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
        CancelJobJob.class.getName()};

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

    public static final String WEBAPP_PREFIX = "candlepin.export.webapp.prefix";

    public static final Map<String, String> DEFAULT_PROPERTIES =
        new HashMap<String, String>() {

            private static final long serialVersionUID = 1L;
            {
                this.put(CANDLEPIN_URL, "https://localhost");

                this.put(CA_KEY, "/etc/candlepin/certs/candlepin-ca.key");
                this.put(CA_CERT, "/etc/candlepin/certs/candlepin-ca.crt");
                this.put(CA_CERT_UPSTREAM,
                    "/etc/candlepin/certs/candlepin-upstream-ca.crt");

                this.put(ACTIVATION_DEBUG_PREFIX, "");

                this.put(HORNETQ_BASE_DIR, "/var/lib/candlepin/hornetq");
                this.put(HORNETQ_LARGE_MSG_SIZE, new Integer(10 * 1024).toString());
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
                this.put(TRUSTED_AUTHENTICATION, "true");
                this.put(SSL_AUTHENTICATION, "true");
                this.put(OAUTH_AUTHENTICATION, "true");
                this.put(BASIC_AUTHENTICATION, "true");

                // By default, environments should be hidden so clients do not need to
                // submit one when registering.
                this.put(HIDDEN_RESOURCES, "environments");

                this.put(FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false");

                // Pinsetter
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

                this.put(WEBAPP_PREFIX, "localhost:8443/candlepin");

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
