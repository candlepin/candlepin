/*
 *  Copyright (c) 2009 - ${YEAR} Red Hat, Inc.
 *
 *  This software is licensed to you under the GNU General Public License,
 *  version 2 (GPLv2). There is NO WARRANTY for this software, express or
 *  implied, including the implied warranties of MERCHANTABILITY or FITNESS
 *  FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 *  along with this software; if not, see
 *  http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 *  Red Hat trademarks are not licensed under GPLv2. No permission is
 *  granted to use or replicate Red Hat trademarks that are incorporated
 *  in this software or its documentation.
 */
package org.candlepin.spec.bootstrap.client;

import org.candlepin.ApiClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;

/**
 * Class to build a Candlpin API instance.
 **/
public class ApiClientFactory {
    private static final Logger log = LoggerFactory.getLogger(ApiClientFactory.class);
    // Create a trust manager that does not validate certificate chains
    private static final TrustManager[] TRUST_ALL_CERTS = new TrustManager[]{
        new X509TrustManager() {
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
    };

    private final Config properties;

    public ApiClientFactory(Config properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    public ApiClient createInstance() {
        ApiClient apiClient = new ApiClient();
        apiClient.setUsername(properties.get(ConfigKey.USERNAME));
        apiClient.setPassword(properties.get(ConfigKey.PASSWORD));
        apiClient.setBasePath(properties.get(ConfigKey.URL));
        apiClient.setDebugging(properties.getBool(ConfigKey.DEBUG));
        apiClient.setVerifyingSsl(false);
        apiClient.setHttpClient(getUnsafeOkHttpClient());

        return apiClient;
    }

    private OkHttpClient getUnsafeOkHttpClient() {
        // Install the all-trusting trust manager
        final SSLContext sslContext = getSslContext(TRUST_ALL_CERTS);
        final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        OkHttpClient.Builder client = new OkHttpClient.Builder();
        client.sslSocketFactory(sslSocketFactory, (X509TrustManager) TRUST_ALL_CERTS[0]);
        client.hostnameVerifier((hostname, session) -> true);
        client.authenticator((route, response) -> {
            String credential = Credentials.basic(properties.get(ConfigKey.USERNAME), properties.get(ConfigKey.PASSWORD));
            return response.request().newBuilder().header("Authorization", credential).build();
        });

        return client.build();
    }

    private static SSLContext getSslContext(TrustManager[] trustAllCerts) {
        try {
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
