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

import org.candlepin.dto.api.v1.CertificateDTO;

/**
 * Static factory created for easy selection of appropriate authentication method.
 */
public final class ApiClients {

    private ApiClients() {
        throw new UnsupportedOperationException();
    }

    private static final ApiClientFactory CLIENT_FACTORY = new ApiClientFactory();

    public static ApiClient activationKey(String owner, String activationKeys) {
        return new ApiClient(CLIENT_FACTORY.createActivationKeyClient(owner, activationKeys));
    }

    public static ApiClient admin() {
        return new ApiClient(CLIENT_FACTORY.createAdminClient());
    }

    public static ApiClient basic(String username, String password) {
        return new ApiClient(CLIENT_FACTORY.createClient(username, password));
    }

    public static ApiClient noAuth() {
        return new ApiClient(CLIENT_FACTORY.createNoAuthClient());
    }

    public static ApiClient trustedConsumer(String consumerUuid) {
        return new ApiClient(CLIENT_FACTORY.createTrustedConsumerClient(consumerUuid));
    }

    public static ApiClient trustedUser(String username) {
        return new ApiClient(CLIENT_FACTORY.createTrustedUserClient(username, false));
    }

    public static ApiClient trustedUser(String username, boolean lookupPermissions) {
        return new ApiClient(CLIENT_FACTORY.createTrustedUserClient(username, lookupPermissions));
    }

    public static ApiClient ssl(CertificateDTO cert) {
        String certificate = cert.getCert() + cert.getKey();
        return new ApiClient(CLIENT_FACTORY.createSslClient(certificate));
    }

}
