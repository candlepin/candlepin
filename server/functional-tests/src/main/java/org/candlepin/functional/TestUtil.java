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
import org.candlepin.client.ApiException;
import org.candlepin.client.model.OwnerDTO;
import org.candlepin.client.resources.OwnersApi;

import org.springframework.beans.factory.annotation.Autowired;

import java.security.SecureRandom;

/** Utility class to perform rote tasks like owner creation */
public class TestUtil {
    private static final String ALPHABET = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static SecureRandom rnd = new SecureRandom();

    @Autowired private static ApiClientFactory clientFactory;


    private TestUtil() {
        // static methods only in this class
    }

    /**
     * @return A nine digit random alphanumeric string
     */
    public static String randomString() {
        return randomString(9);
    }

    /**
     * @param prefix String to be appended to the front of the random string
     * @return A nine digit random alphanumeric string with a chosen prefix added to the front
     */
    public static String randomString(String prefix) {
        return prefix + randomString();
    }

    /**
     * @param prefix String to be appended to the front of the random string
     * @param length The length of the random string to generate
     * @return A given digit random alphanumeric string with a chosen prefix added to the front
     */
    public static String randomString(String prefix, int length) {
        return prefix + randomString(length);
    }

    /**
     * @param length The length of the random string to generate
     * @return A given digit random alphanumeric string
     */
    public static String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(rnd.nextInt(length)));
        }
        return sb.toString();
    }

    public static OwnerDTO trivialOwner() throws Exception {
        return trivialOwner(clientFactory.getObject(), true);
    }

    public static OwnerDTO trivialOwner(String ownerKey) throws Exception {
        return trivialOwner(clientFactory.getObject(), ownerKey);
    }

    public static OwnerDTO trivialOwner(ApiClient client) throws ApiException {
        return trivialOwner(client, true);
    }

    public static OwnerDTO trivialOwner(ApiClient client, boolean randomize) throws ApiException {
        String ownerKey = randomString();
        return trivialOwner(client, ownerKey);
    }

    public static OwnerDTO trivialOwner(ApiClient client, String ownerKey) throws ApiException {
        OwnersApi ownersApi = new OwnersApi(client);
        OwnerDTO owner = new OwnerDTO();
        owner.setKey(ownerKey);
        owner.setDisplayName("Display Name " + ownerKey);
        return ownersApi.createOwner(owner);
    }

}
