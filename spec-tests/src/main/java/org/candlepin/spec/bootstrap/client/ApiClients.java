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

import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;

/**
 * Static factory created for easy selection of appropriate authentication method.
 */
public final class ApiClients {

    private ApiClients() {
        throw new UnsupportedOperationException();
    }

    private static final ApiClientFactory CLIENT_FACTORY = new ApiClientFactory();

    /**
     * Returns a client to make API calls with, that uses one or more activation keys for authentication.
     *
     * @param owner the owner key that the authentication key(s) belong to
     * @param activationKeys a comma separated list of activation keys to authenticate with
     * @return a client to make API calls with that uses one or more activation keys for authentication
     */
    public static ApiClient activationKey(String owner, String activationKeys) {
        return new ApiClient(CLIENT_FACTORY.createActivationKeyClient(owner, activationKeys));
    }

    /**
     * Returns a client to make API calls by using basic authentication with the special 'admin'/'admin'
     * username/password. Equivalent to using ApiClients.basic('admin', 'admin').
     *
     * @return a client to make API calls by using basic authentication with the special
     * 'admin'/'admin' username/password
     */
    public static ApiClient admin() {
        return new ApiClient(CLIENT_FACTORY.createAdminClient());
    }

    /**
     * Returns a client to make API calls with, that uses basic username/password authentication.
     *
     * @param username username of the user
     * @param password password of the user
     * @return a client to make API calls with using basic username/password authentication
     */
    public static ApiClient basic(String username, String password) {
        return new ApiClient(CLIENT_FACTORY.createClient(username, password));
    }

    /**
     * Returns a client to make API calls with, that uses no authentication whatsoever.
     *
     * @return a client to make API calls with that uses no authentication at all
     */
    public static ApiClient noAuth() {
        return new ApiClient(CLIENT_FACTORY.createNoAuthClient());
    }

    /**
     * <b>WARNING: should not be used in spec tests, except for the rare case of testing this feature
     * directly</b>
     * <p></p>
     * Returns a client to make API calls with, that uses the special 'cp-consumer: uuid' header, without
     * doing any actual authentication (except checking that a consumer with that uuid exists), which should
     * only be used by clients in a trusted environment.
     *
     * @param consumerUuid an uuid of the consumer we are trying to authenticate as
     * @return a client to make API calls with that uses the special 'cp-consumer: uuid' header
     */
    public static ApiClient trustedConsumer(String consumerUuid) {
        return new ApiClient(CLIENT_FACTORY.createTrustedConsumerClient(consumerUuid));
    }

    /**
     * <b>WARNING: should not be used in spec tests, except for the rare case of testing this feature
     * directly</b>
     * <p></p>
     * Returns a client to make API calls with, that uses the special 'cp-user: username' header, without
     * doing any actual authentication (except checking that a user with that username exists), which should
     * only be used by clients in a trusted environment.
     *
     * @param username the username of the user we are trying to authenticate as
     * @return a client to make API calls with that uses the special 'cp-user: username' header
     */
    public static ApiClient trustedUser(String username) {
        return new ApiClient(CLIENT_FACTORY.createTrustedUserClient(username, true));
    }

    /**
     * <b>WARNING: should not be used in spec tests, except for the rare case of testing this feature
     * directly</b>
     * <p></p>
     * Returns a client to make API calls with, that uses the special 'cp-user: username' header, and
     * optionally the 'cp-lookup-permissions: true/false' header, without doing any actual authentication
     * (except checking that a user with that username exists), which should only be used by clients in a
     * trusted environment. When cp-lookup-permissions is set to false, then even the username lookup is
     * skipped, which automatically results in granting a principal with super-admin priviledges.
     *
     * @param username the username of the user we are trying to authenticate as
     * @param lookupPermissions if true, the username is checked for existence. if false, it isn't, and a
     * principal with super-admin privileges is granted
     * @return a client to make API calls with that uses the special 'cp-user: username' header
     */
    public static ApiClient trustedUser(String username, boolean lookupPermissions) {
        return new ApiClient(CLIENT_FACTORY.createTrustedUserClient(username, lookupPermissions));
    }

    /**
     * Returns a client to make API calls with, that uses a bearer token authentication.
     *
     * @param token a JWT formatted bearer token
     * @return a client to make API calls with, that uses a bearer token authentication
     */
    public static ApiClient bearerToken(String token) {
        return new ApiClient(CLIENT_FACTORY.createBearerTokenAuthClient(token));
    }

    /**
     * Returns a client to make API calls with, that uses two-way SSL authentication (uses a
     * client/identity certificate and key).
     *
     * @param cert a consumer's certificate DTO to authenticate with
     * @return a client to make API calls with, that uses two-way SSL authentication
     */
    public static ApiClient ssl(CertificateDTO cert) {
        String certificate = cert.getCert() + cert.getKey();
        return new ApiClient(CLIENT_FACTORY.createSslClient(certificate));
    }

    /**
     * Returns a client to make API calls with, that uses two-way SSL authentication (uses a
     * client/identity certificate and key).
     *
     * @param consumer a consumer DTO whose identity certificate will be used to authenticate with
     * @return a client to make API calls with, that uses two-way SSL authentication
     */
    public static ApiClient ssl(ConsumerDTO consumer) {
        if (consumer == null || consumer.getIdCert() == null) {
            throw new IllegalArgumentException("Expected consumer with identity certificate but got null.");
        }
        return ssl(consumer.getIdCert());
    }

}
