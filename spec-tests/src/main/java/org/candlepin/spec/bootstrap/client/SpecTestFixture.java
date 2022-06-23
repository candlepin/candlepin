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

import org.junit.jupiter.api.BeforeAll;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public abstract class SpecTestFixture {

    private Map<Class, Object> clientMap;
    protected ApiClientFactory apiClientFactory;

    @BeforeAll
    void setUp() {
        clientMap = new ConcurrentHashMap<>();
        apiClientFactory = new ApiClientFactory();
    }

    protected <O> O getClient(Class<O> outputClass) {
        O client = (O) clientMap.get(outputClass);
        if (client == null) {
            client = (O) createClient(outputClass);
            clientMap.put(outputClass, client);
        }
        return client;
    }

    /**
     * Factory method to create object clients
     * @param clientClass
     * @param <O>
     * @return Specific client class for API calls
     */
    private <O> O createClient(Class<O> clientClass)  {
        switch (clientClass.getName()) {
            case "org.candlepin.spec.bootstrap.client.OwnerClient":
                return (O) new OwnerClient(apiClientFactory.createAdminClient());
            case "org.candlepin.spec.bootstrap.client.JobsClient":
                return (O) new JobsClient(apiClientFactory.createAdminClient());
            default:
                return null;
        }
    }
}
