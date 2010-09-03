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

package org.fedoraproject.candlepin.config;

import java.util.HashMap;
import java.util.Map;

import org.fedoraproject.candlepin.pinsetter.tasks.CertificateRevocationListTask;
import org.fedoraproject.candlepin.pinsetter.tasks.JobCleaner;
import org.hibernate.tool.hbm2x.StringUtils;

/**
 * Defines a map of default properties used to prepopulate the {@link Config}.
 * Also holds static keys for config lookup.
 */
public class ConfigProperties {
    private ConfigProperties() {
    }

    public static final String CA_KEY = "candlepin.ca_key";
    public static final String CA_CERT = "candlepin.ca_cert";
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

    //AMQP stuff
    public static final String AMQP_INTEGRATION_ENABLED = 
        "candlepin.amqp.enable";
    public static final String AMQP_CONNECT_STRING = "candlepin.amqp.connect";
    public static final String AMQP_KEYSTORE = "candlepin.amqp.keystore";
    public static final String AMQP_KEYSTORE_PASSWORD = "candlepin.amqp.keystore_password";
    public static final String AMQP_TRUSTSTORE = "candlepin.amqp.truststore";
    public static final String AMQP_TRUSTSTORE_PASSWORD =
        "candlepin.amqp.truststore_password";
    
    // Pinsetter
    public static final String TASKS = "pinsetter.tasks";
    public static final String DEFAULT_TASKS = "pinsetter.default_tasks";
    
    private static final String[] DEFAULT_TASK_LIST = new String[] {
        CertificateRevocationListTask.class.getName(),
        JobCleaner.class.getName()
    };
    
    public static final String SYNC_WORK_DIR = "candlepin.sync.work_dir";

    public static final Map<String, String> DEFAULT_PROPERTIES = 
        new HashMap<String, String>() {
            private static final long serialVersionUID = 1L;
            {
                this.put(CA_KEY, "/etc/candlepin/certs/candlepin-ca.key");
                this.put(CA_CERT, "/etc/candlepin/certs/candlepin-ca.crt");
                this.put(CA_CERT_UPSTREAM, 
                    "/etc/candlepin/certs/candlepin-upstream-ca.crt");
                
                this.put(HORNETQ_BASE_DIR, "/var/lib/candlepin/hornetq");
                this.put(HORNETQ_LARGE_MSG_SIZE, new Integer(10 * 1024).toString());
                this.put(AUDIT_LISTENERS,
                    "org.fedoraproject.candlepin.audit.DatabaseListener," +
                    "org.fedoraproject.candlepin.audit.LoggingListener,");
                this.put(AUDIT_LOG_FILE, "/var/log/candlepin/audit.log");
                this.put(AUDIT_LOG_VERBOSE, "false");

                this.put(PRETTY_PRINT, "false");
                this.put(REVOKE_ENTITLEMENT_IN_FIFO_ORDER, "true");
                this.put(CRL_FILE_PATH, "/var/lib/candlepin/candlepin-crl.crl");

                this.put(SYNC_WORK_DIR, "/var/cache/candlepin/sync");

                
                // Pinsetter
                this.put("org.quartz.threadPool.class", 
                    "org.quartz.simpl.SimpleThreadPool");
                this.put("org.quartz.threadPool.threadCount", "15");
                this.put("org.quartz.threadPool.threadPriority", "5");
                this.put(DEFAULT_TASKS, StringUtils.join(DEFAULT_TASK_LIST, ","));
                
                this.put(AMQP_INTEGRATION_ENABLED, String.valueOf(false));
                this.put(AMQP_CONNECT_STRING,
                    "tcp://localhost:5671?ssl='true'&ssl_cert_alias='amqp-client'");
                this.put(AMQP_KEYSTORE, "/etc/candlepin/certs/amqp/keystore");
                this.put(AMQP_KEYSTORE_PASSWORD, "password");
                this.put(AMQP_TRUSTSTORE, "/etc/candlepin/certs/amqp/truststore");
                this.put(AMQP_TRUSTSTORE_PASSWORD, "password");
            }
        };
    public static final String CRL_FILE_PATH = "candlepin.crl.file";
}
