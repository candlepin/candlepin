/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentAccessDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;


@SpecTest
public class ConsumerResourceContentAccessSpecTest {

    private ApiClient adminClient;
    private OwnerDTO owner;

    @BeforeEach
    public void setup() {
        adminClient = ApiClients.admin();
        owner = adminClient.owners().createOwner(Owners.random());
    }

    @Test
    public void shouldGetConsumerContentAccess() {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ContentAccessDTO contAccess = adminClient.consumers().getContentAccessForConsumer(consumer.getUuid());
        assertThat(contAccess)
            .returns(Owners.ENTITLEMENT_ACCESS_MODE, ContentAccessDTO::getContentAccessMode)
            .extracting(ContentAccessDTO::getContentAccessModeList, as(collection(String.class)))
            .containsExactlyInAnyOrder(Owners.ENTITLEMENT_ACCESS_MODE, Owners.SCA_ACCESS_MODE);
    }

    @Test
    public void shouldNotAllowToContentAccessCertificateBodyWhenNotScaMode() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        assertBadRequest(() -> consumerClient.consumers().getContentAccessBody(consumer.getUuid(), null));
    }

    @ParameterizedTest
    @CsvSource(value = {"entitlement:entitlement:entitlement:entitlement",
        "entitlement:entitlement:'':",
        "entitlement:entitlement::entitlement",
        "org_environment:org_environment:org_environment:org_environment",
        "org_environment:org_environment:'':",
        "org_environment:org_environment::org_environment",
        "entitlement,org_environment:entitlement:entitlement:entitlement",
        "entitlement,org_environment:entitlement:org_environment:org_environment",
        "entitlement,org_environment:entitlement:'':",
        "entitlement,org_environment:entitlement::org_environment",
        "entitlement,org_environment:org_environment:entitlement:entitlement",
        "entitlement,org_environment:org_environment:org_environment:org_environment",
        "entitlement,org_environment:org_environment:'':",
        "entitlement,org_environment:org_environment::org_environment"},
        delimiter = ':')
    public void shouldTestScaConsumerCreationForManifest(String ownerModeList, String ownerMode,
        String consumerInputMode, String expectedConsumerMode) {
        OwnerDTO owner1 = adminClient.owners().createOwner(Owners.random()
            .contentAccessModeList(ownerModeList).contentAccessMode(ownerMode));
        UserDTO user = UserUtil.createUser(adminClient, owner1);
        ApiClient userClient = ApiClients.basic(user);

        ConsumerDTO consumer = Consumers.random(owner1)
            .type(ConsumerTypes.Candlepin.value())
            .username(user.getUsername())
            .contentAccessMode(consumerInputMode);
        consumer = userClient.consumers().createConsumer(consumer);
        assertThat(consumer)
            .isNotNull()
            .returns(expectedConsumerMode, ConsumerDTO::getContentAccessMode);
    }

    @ParameterizedTest
    @CsvSource(value = {"entitlement:entitlement:org_environment",
        "org_environment:org_environment:entitlement",
        "entitlement:entitlement:potato",
        "org_environment:org_environment:potato",
        "entitlement,org_environment:entitlement:potato",
        "entitlement,org_environment:org_environment:potato"},
        delimiter = ':')
    public void shouldTestScaConsumerCreationForManifestInvalidModes(String ownerModeList, String ownerMode,
        String consumerInputMode) {
        OwnerDTO owner1 = adminClient.owners().createOwner(Owners.random()
            .contentAccessModeList(ownerModeList).contentAccessMode(ownerMode));
        UserDTO user = UserUtil.createUser(adminClient, owner1);
        ApiClient userClient = ApiClients.basic(user);

        ConsumerDTO consumer = Consumers.random(owner1)
            .type(ConsumerTypes.Candlepin.value())
            .username(user.getUsername())
            .contentAccessMode(consumerInputMode);
        assertBadRequest(() -> userClient.consumers().createConsumer(consumer));
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void shouldTestScaConsumerCreationForSystem(String consumerInputMode) {
        OwnerDTO owner1 = adminClient.owners().createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(adminClient, owner1);
        ApiClient userClient = ApiClients.basic(user);

        ConsumerDTO consumer = Consumers.random(owner1)
            .username(user.getUsername())
            .contentAccessMode(consumerInputMode);
        consumer = userClient.consumers().createConsumer(consumer);
        assertThat(consumer)
            .isNotNull()
            .returns(null, ConsumerDTO::getContentAccessMode);
    }

    @ParameterizedTest
    @ValueSource(strings = {"entitlement", "org_environment", "potato"})
    public void shouldTestScaConsumerCreationForSystemInvalidModes(String consumerInputMode) {
        OwnerDTO owner1 = adminClient.owners().createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(adminClient, owner1);
        ApiClient userClient = ApiClients.basic(user);

        ConsumerDTO consumer = Consumers.random(owner1)
            .username(user.getUsername())
            .contentAccessMode(consumerInputMode);
        assertBadRequest(() -> userClient.consumers().createConsumer(consumer));
    }
}
