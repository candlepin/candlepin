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
package org.fedoraproject.candlepin.client;

import java.io.File;
import java.util.Properties;

/**
 * Configuration.
 */
public final class Configuration {

    private Properties properties;
    private boolean ignoreTrustManagers = false;

    /**
     * @param properties
     */
    public Configuration(Properties properties) {
        this.properties = properties;
    }

    /**
     * Gets the ca certificate file
     * 
     * @return the key store file location
     */
    public String getCACertFile() {
        return this.properties.getProperty(Constants.CP_CERT_LOC);
    }

    /**
     * Gets the server address.
     * 
     * @return the server address
     */
    public String getServerURL() {
        return this.properties.getProperty(Constants.SERVER_URL_KEY);
    }

    /**
     * Gets the candlepin home dir.
     * 
     * @return the candlepin home dir
     */
    public String getCandlepinHomeDir() {
        return this.properties.getProperty(Constants.CP_HOME_DIR);
    }

    /**
     * Gets the consumer directory name.
     * 
     * @return the consumer directory name
     */
    public String getConsumerDirPath() {
        return new StringBuilder().append(getCandlepinHomeDir()).append(
            File.separator).append("consumer").toString();
    }

    public String getConsumerIdentityFilePath() {
        return new StringBuilder().append(getConsumerDirPath()).append(
            File.separator).append("identity.pem").toString();
    }

    /**
     * Gets the entitlement directory name.
     * 
     * @return the entitlement directory name
     */
    public String getEntitlementDirPath() {
        return new StringBuilder().append(getCandlepinHomeDir()).append(
            File.separator).append("entitlements").toString();
    }

    /**
     * Gets the product directory name.
     * 
     * @return the product directory name
     */
    public String getProductDirPath() {
        return new StringBuilder().append(getCandlepinHomeDir()).append(
            File.separator).append("products").toString();
    }

    public boolean isIgnoreTrustManagers() {
        return ignoreTrustManagers;
    }

    public void setIgnoreTrustManagers(boolean ignoreTrustManagers) {
        this.ignoreTrustManagers = ignoreTrustManagers;
    }
}
