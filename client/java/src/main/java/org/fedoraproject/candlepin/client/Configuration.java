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

import org.apache.commons.lang.StringUtils;

/**
 * Configuration.
 */
public final class Configuration {

    private Properties properties;

    /**
     * @param properties
     */
    public Configuration(Properties properties) {
        this.properties = properties;
    }

    /**
     * Gets the key store file location.
     *
     * @return the key store file location
     */
    public String getKeyStoreFileLocation() {
        return this.properties.getProperty(Constants.KEY_STORE_LOC);
    }

    public String getKeyStorePassword() {
        return StringUtils.defaultIfEmpty(
            this.properties.getProperty(Constants.KEY_STORE_PASSWD),
            Constants.DEFAULT_KEY_STORE_PASSWD);
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
     * Gets the candlepin certificate file.
     *
     * @return the candlepin certificate file
     */
    public String getCandlepinCertificateFile() {
        return this.properties.getProperty(Constants.CP_CERT_LOC);
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
        return new StringBuilder().append(getCandlepinHomeDir())
                .append(File.separator).append("consumer").toString();
    }

    /**
     * Gets the certificate file name.
     *
     * @return the certificate file name
     */
    public String getCertificateFilePath() {
        return new StringBuilder().append(getConsumerDirPath())
                .append(File.separator).append("cert.pem").toString();
    }

    /**
     * Gets the key file name.
     *
     * @return the key file name
     */
    public String getKeyFilePath() {
        return new StringBuilder().append(getConsumerDirPath()).append(
            File.separator).append("key.pem").toString();
    }

    /**
     * Gets the entitlement directory name.
     *
     * @return the entitlement directory name
     */
    public String getEntitlementDirPath() {
        return new StringBuilder().append(getCandlepinHomeDir())
                .append(File.separator).append("entitlements").toString();
    }

    /**
     * Gets the product directory name.
     *
     * @return the product directory name
     */
    public String getProductDirPath() {
        return new StringBuilder().append(getCandlepinHomeDir())
                .append(File.separator).append("products").toString();
    }
}
