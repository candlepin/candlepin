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

package org.candlepin.spec.consumers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.dto.api.client.v1.ActivationKeyDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentOverrideDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.resource.client.v1.ActivationKeyApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

@SpecTest
public class ConsumerResourceActivationKeySpecTest {

    static ApiClient client;
    static OwnerClient ownerClient;
    static ConsumerClient consumerClient;
    static ActivationKeyApi activationKeyApi;


    @BeforeAll
    static void beforeAll() {
        client = ApiClients.admin();
        ownerClient = client.owners();
        consumerClient = client.consumers();
        activationKeyApi = client.activationKeys();
    }

    @Test
    public void shouldAllowConsumerToRegisterWithMultipleActivationKeysAndRetainOrder() throws Exception {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ActivationKeyDTO key1 = new ActivationKeyDTO().name(StringUtil.random("key"));
        ActivationKeyDTO key2 = new ActivationKeyDTO().name(StringUtil.random("key"));
        String contentName = StringUtil.random("name");
        String contentLabel = StringUtil.random("label");
        ContentOverrideDTO override1 = new ContentOverrideDTO()
            .name(contentName)
            .value("value1")
            .contentLabel(contentLabel);
        key1 = key1.contentOverrides(Set.of(override1));
        key1 = ownerClient.createActivationKey(owner.getKey(), key1);
        ContentOverrideDTO override2 = new ContentOverrideDTO()
            .name(contentName)
            .value("value2")
            .contentLabel(contentLabel);
        key2 = key2.contentOverrides(Set.of(override2));
        key2 = ownerClient.createActivationKey(owner.getKey(), key2);

        ConsumerDTO consumer1 = Consumers.random(owner)
            .name(StringUtil.random("system"))
            .type(ConsumerTypes.System.value());
        consumer1 = consumerClient.createConsumer(consumer1, null, owner.getKey(),
            key1.getName() + "," + key2.getName(), true);
        List<ContentOverrideDTO> consumerOverrides =
            consumerClient.listConsumerContentOverrides(consumer1.getUuid());
        assertThat(consumerOverrides).hasSize(1);
        assertEquals("value2", consumerOverrides.get(0).getValue());

        ConsumerDTO consumer2 = Consumers.random(owner)
            .name(StringUtil.random("system"))
            .type(ConsumerTypes.System.value());
        consumer2 = consumerClient.createConsumer(consumer2, null, owner.getKey(),
            key2.getName() + "," + key1.getName(), true);
        consumerOverrides = consumerClient.listConsumerContentOverrides(consumer2.getUuid());
        assertThat(consumerOverrides).hasSize(1);
        assertEquals("value1", consumerOverrides.get(0).getValue());
    }
}
