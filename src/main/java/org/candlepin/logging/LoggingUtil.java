/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.logging;



/**
 * The LoggingUtil class provides variables and utility functionality for the logging subsystems
 * used within Candlepin
 */
public class LoggingUtil {

    /** The MDC key for specifying the request type in log messages */
    public static final String MDC_REQUEST_TYPE_KEY = "requestType";

    /** The MDC key for specifying the request UUID in log messages */
    public static final String MDC_REQUEST_UUID_KEY = "requestUuid";

    /** The MDC key for specifying the context owner for the request or job in log messages */
    public static final String MDC_OWNER_KEY = "org";

    /** The MDC key for specifying the job key in log messages */
    public static final String MDC_JOB_KEY_KEY = "jobKey";

    /** The MDC key for specifying the correlation ID, or correlated service ID */
    public static final String MDC_CSID_KEY = "csid";

    /**
     * The MDC key for specifying the log level to use for logging. While this key is set, the
     * value associated with the key will be used for logging, overriding whatever is configured
     * in logback.xml and/or candlepin.conf.
     *
     * Valid values associated with this key are: ALL, TRACE, DEBUG, INFO, WARN, ERROR, OFF
     */
    public static final String MDC_LOG_LEVEL_KEY = "logLevel";


    private LoggingUtil() {
        // Intentionally left empty; instantiation disabled for utility classes
    }

}
