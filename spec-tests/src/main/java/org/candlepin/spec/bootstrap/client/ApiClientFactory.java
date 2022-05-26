/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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
package org.candlepin.spec.bootstrap.client;

import org.candlepin.ApiClient;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;

/**
 * Class to build a Candlepin API instance.
 **/
public class ApiClientFactory {
    private static final Logger log = LoggerFactory.getLogger(ApiClientFactory.class);

    private static final TrustManager[] TRUST_ALL_CERTS = {new TrustAllManager()};

    private static PropertiesConfiguration properties;
    private final Object propertyLock = new Object();

    public ApiClientFactory() {
        synchronized (propertyLock) {
            if (properties == null) {
                properties = new PropertiesConfiguration(
                    new DefaultProperties(),
                    // TODO add support for external properties
                    new SystemProperties());
            }
        }
    }

    public ApiClient createDefaultClient() {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(getUrl());
        apiClient.setDebugging(properties.getBool(ConfigKey.DEBUG.key()));
        apiClient.setVerifyingSsl(false);

        return apiClient;
    }

    public ApiClient createNoAuthClient() {
        ApiClient apiClient = createDefaultClient();
        apiClient.setHttpClient(createOkHttpClient());

        return apiClient;
    }

    public ApiClient createActivationKeyClient(String owner, String activationKeys) {
        ApiClient apiClient = createDefaultClient();
        apiClient.setHttpClient(createOkHttpClient(new ActivationKeyInterceptor(owner, activationKeys)));

        return apiClient;
    }

    public ApiClient createTrustedConsumerClient(String consumerUuid) {
        ApiClient apiClient = createDefaultClient();
        apiClient.setHttpClient(createOkHttpClient(new TrustedConsumerInterceptor(consumerUuid)));

        return apiClient;
    }

    public ApiClient createTrustedUserClient(String consumerUuid, boolean lookupPermissions) {
        ApiClient apiClient = createDefaultClient();
        apiClient.setHttpClient(createOkHttpClient(
            new TrustedUserInterceptor(consumerUuid, lookupPermissions)));

        return apiClient;
    }

    public ApiClient createAdminClient() {
        String username = properties.getProperty(ConfigKey.USERNAME.key());
        String password = properties.getProperty(ConfigKey.PASSWORD.key());

        return createClient(username, password);
    }

    public ApiClient createClient(String username, String password) {
        ApiClient apiClient = createDefaultClient();
        apiClient.setUsername(username);
        apiClient.setPassword(password);
        apiClient.setHttpClient(createOkHttpClient(new BasicAuthInterceptor(username, password)));

        return apiClient;
    }

    public ApiClient createSslClient(String cert) {
        ApiClient apiClient = createDefaultClient();
        apiClient.setHttpClient(createSslOkHttpClient(cert));

        return apiClient;
    }

    private OkHttpClient createOkHttpClient(Interceptor... interceptors) {
        // Install the all-trusting trust manager
        final SSLContext sslContext = getSslContext(TRUST_ALL_CERTS);
        final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        OkHttpClient.Builder clientBuilder = createClientBuilder();
        clientBuilder.sslSocketFactory(sslSocketFactory, (X509TrustManager) TRUST_ALL_CERTS[0]);

        for (Interceptor interceptor : interceptors) {
            clientBuilder.addInterceptor(interceptor);
        }

        return clientBuilder.build();
    }

    public OkHttpClient createSslOkHttpClient(String cert) {
        // See https://square.github.io/okhttp/4.x/okhttp-tls/okhttp3.tls/-held-certificate/decode/
        HeldCertificate clientCertificate = HeldCertificate.decode(cert);

        HandshakeCertificates clientCertificates = new HandshakeCertificates.Builder()
            .heldCertificate(clientCertificate)
            // disable server cert validation
            .addInsecureHost(properties.getProperty(ConfigKey.HOST.key()))
            .build();

        return createClientBuilder()
            //disable the server cert's hostname verification
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager())
            .build();
    }

    private OkHttpClient.Builder createClientBuilder() {
        OkHttpClient.Builder client = new OkHttpClient.Builder();

        return client.hostnameVerifier((hostname, session) -> true)
            .connectTimeout(properties.getLong(ConfigKey.CONNECT_TIMEOUT), TimeUnit.SECONDS)
            .writeTimeout(properties.getLong(ConfigKey.CONNECT_TIMEOUT), TimeUnit.SECONDS)
            .readTimeout(properties.getLong(ConfigKey.CONNECT_TIMEOUT), TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false);
    }

    private SSLContext getSslContext(TrustManager[] trustAllCerts) {
        try {
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getUrl() {
        return String.format("https://%s:%s%s",
            properties.getProperty(ConfigKey.HOST.key()),
            properties.getProperty(ConfigKey.PORT.key()),
            properties.getProperty(ConfigKey.PREFIX.key()));
    }

    /**
     * A trust manager that does not validate certificate chains
     */
    private static class TrustAllManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{};
        }
    }

    /**
     * Interceptor that enables use of activation key auth for endpoints that
     * do not define it in openAPI.
     */
    private static class ActivationKeyInterceptor implements Interceptor {

        private final String ownerKey;
        private final String activationKeys;

        public ActivationKeyInterceptor(String ownerKey, String activationKeys) {
            this.ownerKey = ownerKey;
            this.activationKeys = activationKeys;
        }

        @NotNull
        @Override
        public Response intercept(Interceptor.Chain chain) throws IOException {
            HttpUrl.Builder urlBuilder = chain.request().url().newBuilder();
            addQueryParam(urlBuilder, "owner", this.ownerKey);
            addQueryParam(urlBuilder, "activation_keys", this.activationKeys);

            Request request = chain.request().newBuilder()
                .url(urlBuilder.build())
                .build();
            return chain.proceed(request);
        }

        private void addQueryParam(HttpUrl.Builder builder, String key, String value) {
            if (value != null && !value.isBlank()) {
                builder.addQueryParameter(key, value);
            }
        }
    }

    private static class TrustedConsumerInterceptor implements Interceptor {

        private final String consumerUuid;

        public TrustedConsumerInterceptor(String consumerUuid) {
            if (consumerUuid == null || consumerUuid.isBlank()) {
                throw new IllegalArgumentException("Consumer UUID is required!");
            }
            this.consumerUuid = consumerUuid;
        }

        @NotNull
        @Override
        public Response intercept(Interceptor.Chain chain) throws IOException {
            Request request = chain.request().newBuilder()
                .header("cp-consumer", this.consumerUuid)
                .build();
            return chain.proceed(request);
        }
    }

    private static class TrustedUserInterceptor implements Interceptor {

        private final String username;
        private final String lookupPermissions;

        public TrustedUserInterceptor(String username, boolean lookupPermissions) {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("Username is required!");
            }
            this.username = username;
            this.lookupPermissions = Boolean.toString(lookupPermissions);
        }

        @NotNull
        @Override
        public Response intercept(Interceptor.Chain chain) throws IOException {
            Request request = chain.request().newBuilder()
                .header("cp-user", this.username)
                .header("cp-lookup-permissions", this.lookupPermissions)
                .build();
            return chain.proceed(request);
        }
    }

    public static class BasicAuthInterceptor implements Interceptor {

        private final String credentials;

        public BasicAuthInterceptor(String user, String password) {
            this.credentials = Credentials.basic(user, password);
        }

        @NotNull
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request().newBuilder()
                .header("Authorization", credentials)
                .build();
            return chain.proceed(request);
        }

    }

}
