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
import org.candlepin.client.Configuration;
import org.candlepin.client.auth.HttpBasicAuth;

import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

/**
 * Class to build a Candlpin API instance.
 **/
@Component
public class ApiClientFactory extends AbstractFactoryBean<ApiClient> {
    private static final Logger log = LoggerFactory.getLogger(ApiClientFactory.class);

    private ApiClientProperties properties;

    @Autowired
    public ApiClientFactory(ApiClientProperties properties) {
        this.properties = properties;
    }

    public ApiClientFactory() {
        // For the ApiClientBuilder class to use
    }

    public void setApiClientProperties(ApiClientProperties properties) {
        this.properties = properties;
    }

    @Override
    public ApiClient createInstance() {
        if (properties == null) {
            throw new IllegalStateException("The ApiClientProperties field is null");
        }

        ApiClient apiClient = Configuration.getDefaultApiClient();
        try {
            apiClient.setHttpClient(buildHttpClient(apiClient));
        }
        catch (GeneralSecurityException e) {
            throw new BeanCreationException("Could not create X509ApiClient bean", e);
        }

        if (properties.usesClientAuth() && properties.usesBasicAuth()) {
            throw new IllegalStateException("Both X509 client auth and basic auth are configured.");
        }

        if (properties.usesBasicAuth()) {
            log.debug("Creating client using basic auth");
            HttpBasicAuth auth = (HttpBasicAuth) apiClient.getAuthentication("basic");
            auth.setUsername(properties.getUsername());
            auth.setPassword(properties.getPassword());
        }

        if (properties.getUrl() != null) {
            apiClient.setBasePath(properties.getUrl());
        }
        return apiClient;
    }

    private Client buildHttpClient(ApiClient client)
        throws GeneralSecurityException {
        ClientConfiguration clientConfig = new ClientConfiguration(ResteasyProviderFactory.getInstance());
        clientConfig.register(client.getJSON());
        if (client.isDebugging()) {
            clientConfig.register(Logger.class);
        }

        ClientBuilder builder = ClientBuilder.newBuilder().withConfig(clientConfig);

        TrustManager[] trustManagers;
        if (properties.isInsecure()) {
            builder.hostnameVerifier(new NoopHostnameVerifier());
            trustManagers = createInsecureTrustManager();
        }
        else {
            builder.hostnameVerifier(new DefaultHostnameVerifier());
            trustManagers = createSecureTrustManager(properties);
        }

        KeyManager[] keyManagers = null;
        if (properties.usesClientAuth()) {
            log.debug("Creating client using X509 client cert auth");
            KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm()
            );
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            try {
                keystore.load(properties.getKeystoreStream(), properties.getKeystorePassword().toCharArray());
                keyFactory.init(keystore, properties.getKeystorePassword().toCharArray());
                keyManagers = keyFactory.getKeyManagers();
            }
            catch (IOException e) {
                throw new GeneralSecurityException("Failed to init SSLContext", e);
            }
        }

        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(keyManagers, trustManagers, null);
        builder.sslContext(sslContext);
        return builder.build();
    }

    private TrustManager[] createInsecureTrustManager() {
        TrustManager[] naiveManager = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                    // no op
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                    // no op
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };

        return naiveManager;
    }

    private TrustManager[] createSecureTrustManager(ApiClientProperties x509Config)
        throws GeneralSecurityException {

        // Passing a null TrustManager in will result in the default Java CA bundle being loaded
        TrustManager[] trustManagers = null;

        if (x509Config.providesTruststore()) {
            TrustManagerFactory trustFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore truststore;
            truststore = KeyStore.getInstance(KeyStore.getDefaultType());
            try {
                truststore.load(
                    x509Config.getTruststoreStream(),
                    x509Config.getTruststorePassword().toCharArray()
                );
                trustFactory.init(truststore);
                trustManagers = trustFactory.getTrustManagers();
            }
            catch (IOException e) {
                throw new GeneralSecurityException("Failed to init SSLContext", e);
            }
        }

        return trustManagers;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    @Override
    public Class<?> getObjectType() {
        return ApiClient.class;
    }
}
