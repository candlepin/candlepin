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
package org.candlepin.client;

import org.apache.commons.lang.SystemUtils;

/**
 * The Interface Constants.
 */
public final class Constants {

    private Constants() {
        // dont instantiate it!
    }

    /** The PRO d_ nam e_ ext n_ val. */

    /** The STAR t_ date. */
    public static final String START_DATE = "1.3.6.1.4.1.2312.9.4.6";

    /** The EN d_ date. */
    public static final String END_DATE = "1.3.6.1.4.1.2312.9.4.7";

    /** The PRO d_ i d_ begin. */
    public static final String PRODUCT_NAMESPACE = "1.3.6.1.4.1.2312.9.1";
    public static final String CONTENT_NAMESPACE = "1.3.6.1.4.1.2312.9.2";
    public static final String ROLE_ENTITLEMENT_NAMESPACE = "1.3.6.1.4.1.2312.9.3";
    public static final String ORDER_NAMESPACE = "1.3.6.1.4.1.2312.9.4";
    public static final String SYSTEM_NAMESPACE = "1.3.6.1.4.1.2312.9.5";

    public static final String X509 = "X509";
    public static final String SERVER_URL_KEY = "serverURL";
    public static final String CP_HOME_DIR = "candlePinHomeDirectory";
    public static final String CP_CERT_LOC = "candlepinCertificate";

    public static final String CONFIG_LOCATION = "configLoc";

    public static final String DEFAULT_CONF_LOC = SystemUtils.USER_HOME +
        "/.candlepin/candlepin.conf";
    public static final String DEFAULT_KEY_STORE_PASSWD = "password";

    public static final Object ERR_DISPLAY_MSG = "displayMessage";
}
