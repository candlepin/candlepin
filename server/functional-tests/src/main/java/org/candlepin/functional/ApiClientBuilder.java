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

import org.candlepin.client.ApiClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;

/**
 * Allows tests to build a custom ApiClient using a fluent interface.
 */
@Component
public class ApiClientBuilder {
    private static final Logger log = LoggerFactory.getLogger(ApiClientBuilder.class);

    private final RestTemplateBuilder templateBuilder;
    private final ApiClientProperties apiClientProperties;

    @Autowired
    public ApiClientBuilder(ApiClientProperties coreProperties, RestTemplateBuilder templateBuilder) {
        // Seed the apiClientProperties with the default properties coming from the application configuration
        this.apiClientProperties = new ApiClientProperties(coreProperties);
        this.templateBuilder = templateBuilder;
    }

    public ApiClientBuilder withUsername(String username) {
        if (apiClientProperties.usesClientAuth()) {
            throw new IllegalStateException("X509 client auth is already configured");
        }
        apiClientProperties.setUsername(username);
        return this;
    }

    public ApiClientBuilder withPassword(String password) {
        if (apiClientProperties.usesClientAuth()) {
            throw new IllegalStateException("X509 client auth is already configured");
        }
        apiClientProperties.setPassword(password);
        return this;
    }

    public ApiClientBuilder withUrl(String url) {
        apiClientProperties.setUrl(url);
        return this;
    }

    public ApiClientBuilder withDebug(boolean isDebug) {
        apiClientProperties.setDebug(isDebug);
        return this;
    }

    public ApiClientBuilder withTruststore(String truststoreFile, String truststorePassword) {
        apiClientProperties.setTruststoreFile(truststoreFile);
        apiClientProperties.setTruststorePassword(truststorePassword);
        return this;
    }

    public ApiClientBuilder withX509ClientAuth(InputStream certificate, InputStream key) {
        // TODO we need to take the certificate and key and combine them into an instance of the Keystore
        //  class and then write that Keystore to a ByteArrayOutputStream, then read that stream via an
        //  InputStream and send that InputStream to the X509ClientProperties.  It seems a bit absurd but
        //  all that complexity should just be hidden inside this particular method

        if (apiClientProperties.usesBasicAuth()) {
            throw new IllegalStateException("HTTP Basic Auth is already configured");
        }
        throw new RuntimeException("Not implemented yet");
    }

    public ApiClient build() {
        try {
            return new ApiClientFactory(apiClientProperties, templateBuilder).getObject();
        }
        catch (Exception e) {
            log.error("Could not create ApiClient", e);
            throw new BeanCreationException("Could not create ApiClient");
        }
    }
}
