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

import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.function.Supplier;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
/**
 * Class to build a Candlpin API instance.
 **/
@Component
public class ApiClientFactory extends AbstractFactoryBean<ApiClient> {
    private static final Logger log = LoggerFactory.getLogger(ApiClientFactory.class);

    private final ApiClientProperties properties;
    private RestTemplateBuilder templateBuilder;

    @Autowired
    public ApiClientFactory(ApiClientProperties properties, RestTemplateBuilder templateBuilder) {
        this.properties = properties;
        this.templateBuilder = templateBuilder;
    }

    @Override
    @NonNull
    public ApiClient createInstance() {
        if (properties == null) {
            throw new IllegalStateException("The ApiClientProperties field is null");
        }

        if (properties.usesClientAuth() && properties.usesBasicAuth()) {
            throw new IllegalStateException("Both X509 client auth and basic auth are configured.");
        }

        RestTemplate template = templateBuilder.requestFactory(new X509RequestFactory(properties)).build();
        ApiClient apiClient = new ApiClient(template);

        if (properties.usesBasicAuth()) {
            apiClient.setUsername(properties.getUsername());
            apiClient.setPassword(properties.getPassword());
        }

        if (properties.getUrl() != null) {
            apiClient.setBasePath(properties.getUrl());
        }

        apiClient.setDebugging(properties.getDebug());

        return apiClient;
    }

    /** Class that builds a RequestFactory that is SSL/TLS enabled */
    public class X509RequestFactory implements Supplier<ClientHttpRequestFactory> {
        private ApiClientProperties properties;

        public X509RequestFactory(ApiClientProperties properties) {
            this.properties = properties;
        }

        @Override
        public ClientHttpRequestFactory get() {
            HttpClientBuilder clientBuilder = HttpClientBuilder.create();
            try {
                clientBuilder.setSSLContext(x509SSLContext());
            }
            catch (GeneralSecurityException e) {
                throw new RuntimeException("Could not create SSL context for ApiClient", e);
            }
            if (properties.isInsecure()) {
                clientBuilder.setSSLHostnameVerifier(new NoopHostnameVerifier());
            }
            else {
                clientBuilder.setSSLHostnameVerifier(new DefaultHostnameVerifier());
            }

            CloseableHttpClient httpClient = clientBuilder.build();

            return new BufferingClientHttpRequestFactory(
                new HttpComponentsClientHttpRequestFactory(httpClient));
        }

        private SSLContext x509SSLContext() throws GeneralSecurityException {
            TrustManager[] trustManagers;
            if (properties.isInsecure()) {
                trustManagers = createInsecureTrustManager();
            }
            else {
                trustManagers = createSecureTrustManager(properties);
            }

            KeyManager[] keyManagers = null;
            if (properties.usesClientAuth()) {
                log.debug("Creating client using X509 client cert auth");
                KeyManagerFactory keyFactory =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                try {
                    keystore.load(
                        properties.getKeystoreStream(),
                        properties.getKeystorePassword().toCharArray()
                    );
                    keyFactory.init(keystore, properties.getKeystorePassword().toCharArray());
                    keyManagers = keyFactory.getKeyManagers();
                }
                catch (IOException e) {
                    throw new GeneralSecurityException("Failed to init SSLContext", e);
                }
            }

            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        }

        private TrustManager[] createInsecureTrustManager() {
            TrustManager[] naiveManager = new TrustManager[] { new X509TrustManager() {

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
            } };

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
