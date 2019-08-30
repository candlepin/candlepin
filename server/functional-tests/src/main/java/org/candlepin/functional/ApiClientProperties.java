/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.functional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.IOException;
import java.io.InputStream;

/** Class to hold basic client details */
@ConfigurationProperties("functional-tests.client")
public class ApiClientProperties {
    private String url;
    private String username;
    private String password;
    private boolean debug;

    private X509ClientProperties x509Config = new X509ClientProperties();

    @Autowired
    public ApiClientProperties() {
    }

    /**
     * A copy constructor.
     * @param properties values from this object will be copied into the new ApiClientProperties object.
     */
    public ApiClientProperties(ApiClientProperties properties) {
        this.url = properties.getUrl();
        this.debug = properties.getDebug();
        this.username = properties.getUsername();
        this.password = properties.getPassword();

        setInsecure(properties.isInsecure());
        setKeystoreFile(properties.getKeystoreFile());
        setKeystorePassword(properties.getKeystorePassword());
        setTruststoreFile(properties.getTruststoreFile());
        setTruststorePassword(properties.getTruststorePassword());
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean getDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public X509ClientProperties getX509Config() {
        return x509Config;
    }

    public void setX509Config(X509ClientProperties x509Config) {
        this.x509Config = x509Config;
    }

    public X509ClientProperties getX509ApiClientFactoryConfiguration() {
        return x509Config;
    }

    public boolean isInsecure() {
        return x509Config.isInsecure();
    }

    public void setInsecure(boolean insecure) {
        x509Config.setInsecure(insecure);
    }

    public String getKeystorePassword() {
        return x509Config.getKeystorePassword();
    }

    public void setKeystorePassword(String keystorePassword) {
        x509Config.setKeystorePassword(keystorePassword);
    }

    public String getTruststorePassword() {
        return x509Config.getTruststorePassword();
    }

    public void setTruststorePassword(String truststorePassword) {
        x509Config.setTruststorePassword(truststorePassword);
    }

    public String getKeystoreFile() {
        return x509Config.getKeystoreFile();
    }

    public void setKeystoreFile(String keystoreFile) {
        x509Config.setKeystoreFile(keystoreFile);
    }

    public String getTruststoreFile() {
        return x509Config.getTruststoreFile();
    }

    public void setTruststoreFile(String truststoreFile) {
        x509Config.setTruststoreFile(truststoreFile);
    }

    public InputStream getKeystoreStream() throws IOException {
        return x509Config.getKeystoreStream();
    }

    public InputStream getTruststoreStream() throws IOException {
        return x509Config.getTruststoreStream();
    }

    public boolean usesClientAuth() {
        return x509Config.usesClientAuth();
    }

    public boolean providesTruststore() {
        return x509Config.providesTruststore();
    }

    public boolean usesBasicAuth() {
        return username != null && password != null;
    }
}
