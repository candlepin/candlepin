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

package org.candlepin.spec.owners;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.Test;

import java.util.Set;

@SpecTest
public class OwnerResourceCountingFeatureSpecTest {

    @Test
    public void shouldCountAllConsumersOfGivenOwner() throws ApiException {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ApiClient userClient = createUserClient(adminClient, owner);

        userClient.consumers().createConsumer(Consumers.random(owner));
        userClient.consumers().createConsumer(Consumers.random(owner));
        Integer count = userClient.owners().countConsumers(owner.getKey(), null, null, null, null);
        assertEquals(2, count);

        OwnerDTO otherOwner = createOwner(adminClient);
        ApiClient otherUserClient = createUserClient(adminClient, otherOwner);

        otherUserClient.consumers().createConsumer(Consumers.random(otherOwner));
        count = adminClient.owners().countConsumers(otherOwner.getKey(), null, null, null, null);
        assertEquals(1, count);
    }

    @Test
    public void shouldCountOnlyConsumersSpecifiedByTypeLabel() throws ApiException {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ApiClient userClient = createUserClient(adminClient, owner);

        userClient.consumers().createConsumer(Consumers.random(owner, ConsumerTypes.System));
        userClient.consumers().createConsumer(Consumers.random(owner, ConsumerTypes.Hypervisor));

        Integer count = userClient.owners().countConsumers(
            owner.getKey(), null, Set.of("system"), null, null);
        assertEquals(1, count);

        count = userClient.owners().countConsumers(
            owner.getKey(), null, Set.of("system", "hypervisor"), null, null);
        assertEquals(2, count);
    }

    private static ApiClient createUserClient(ApiClient client, OwnerDTO owner) throws ApiException {
        UserDTO user = UserUtil.createUser(client, owner);
        return ApiClients.basic(user.getUsername(), user.getPassword());
    }

    private static OwnerDTO createOwner(ApiClient client) throws ApiException {
        return client.owners().createOwner(Owners.random());
    }
}
