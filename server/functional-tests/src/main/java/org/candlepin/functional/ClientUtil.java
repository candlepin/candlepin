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
import org.candlepin.client.model.UserDTO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Utility for creating various ApiClients
 */
@Component
public class ClientUtil {
    private static final Logger log = LoggerFactory.getLogger(ClientUtil.class);
    private final ApiClientBuilder apiClientBuilder;

    private ApiClient adminApiClient;
    private ApiClientProperties coreProperties;
    private TestUtilFactory testUtilFactory;

    @Autowired
    public ClientUtil(@Qualifier("adminApiClient") ApiClient adminApiClient,
        ApiClientProperties coreProperties, ApiClientBuilder apiClientBuilder,
        TestUtilFactory testUtilFactory) {
        this.adminApiClient = adminApiClient;
        this.coreProperties = coreProperties;
        this.apiClientBuilder = apiClientBuilder;
        this.testUtilFactory = testUtilFactory;
    }

    public ApiClient newReadOnlyUserAndClient(String username, String ownerKey)
        throws RestClientException {
        TestUtil testUtil = testUtilFactory.createInstance(apiClientBuilder);
        String password = TestUtil.randomString();
        UserDTO user = testUtil.createUser(username, password);
        testUtil.createReadOnlyAccessRoleForUser(ownerKey, user);

        return apiClientBuilder
            .withUsername(username)
            .withPassword(password)
            .withDebug(coreProperties.getDebug())
            .build();
    }

    public ApiClient newUserAndClient(String username, String ownerKey)
        throws RestClientException {
        TestUtil testUtil = testUtilFactory.createInstance(apiClientBuilder);
        String password = TestUtil.randomString();
        UserDTO user = testUtil.createUser(username, password);
        testUtil.createAllAccessRoleForUser(ownerKey, user);

        return apiClientBuilder
            .withUsername(username)
            .withPassword(password)
            .withDebug(coreProperties.getDebug())
            .build();
    }

    public ApiClient newSuperadminAndClient(String username, String ownerKey)
        throws RestClientException {
        TestUtil testUtil = testUtilFactory.createInstance(apiClientBuilder);
        String password = TestUtil.randomString();
        UserDTO superadmin = testUtil.createSuperadminUser(username, password);
        testUtil.createAllAccessRoleForUser(ownerKey, superadmin);

        return apiClientBuilder
            .withUsername(username)
            .withPassword(password)
            .withDebug(coreProperties.getDebug())
            .build();
    }
}
