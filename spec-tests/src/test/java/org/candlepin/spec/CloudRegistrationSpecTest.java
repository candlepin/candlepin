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

package org.candlepin.spec;

import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.CloudRegistrationDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.HostedTestApi;
import org.candlepin.resource.client.v1.CloudRegistrationApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.assertions.OnlyWithCapability;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;


@SpecTest
@OnlyInHosted
@OnlyWithCapability("cloud_registration")
class CloudRegistrationSpecTest {

    @Test
    public void shouldGenerateValidTokenWithValidMetadata() throws ApiException {
        ApiClient adminClient = ApiClients.admin();
        HostedTestApi upstreamClient = adminClient.hosted();
        CloudRegistrationApi cloudRegistration = adminClient.cloudAuthorization();
        OwnerDTO owner = upstreamClient.createOwner(Owners.random());
        String key = cloudRegistration.cloudAuthorize(generateToken(owner.getKey(),
            "test_type", "test_signature"));
        assertNotNull(key);
    }

    @Test
    public void shouldAllowRegistrationWithValidToken() throws ApiException {
        ApiClient adminClient = ApiClients.admin();
        HostedTestApi upstreamClient = adminClient.hosted();
        CloudRegistrationApi cloudRegistration = adminClient.cloudAuthorization();
        OwnerDTO owner = upstreamClient.createOwner(Owners.random());
        String key = cloudRegistration.cloudAuthorize(generateToken(owner.getKey(), "test_type",
            "test_signature"));
        ConsumerDTO consumer = ApiClients.cloudAuthUser(key).consumers()
            .createConsumer(Consumers.random(owner));
        assertNotNull(consumer);
    }

    @ParameterizedTest
    @MethodSource("tokenVariation")
    public void shouldFailCloudRegistration(String owner, String type, String signature) {
        ApiClient adminClient = ApiClients.admin();
        CloudRegistrationApi cloudRegistration = adminClient.cloudAuthorization();
        assertBadRequest(() -> cloudRegistration.cloudAuthorize(generateToken(owner, type, signature)));
    }

    @Test
    public void shouldAllowRegistrationWithEmptySignature() throws ApiException {
        ApiClient adminClient = ApiClients.admin();
        HostedTestApi upstreamClient = adminClient.hosted();
        CloudRegistrationApi cloudRegistration = adminClient.cloudAuthorization();
        OwnerDTO owner = upstreamClient.createOwner(Owners.random());
        String key = cloudRegistration.cloudAuthorize(generateToken(owner.getKey(),
            "test_type", ""));
        ConsumerDTO consumer = ApiClients.cloudAuthUser(key).consumers()
            .createConsumer(Consumers.random(owner));
        assertNotNull(consumer);
    }

    private CloudRegistrationDTO generateToken(String owner, String type, String signature) {
        return new CloudRegistrationDTO()
            .type(type)
            .metadata(owner)
            .signature(signature);
    }

    private static Stream<Arguments> tokenVariation() throws ApiException {
        OwnerDTO owner = ApiClients.admin().hosted().createOwner(Owners.random());

        return Stream.of(
            Arguments.of(null, null, null),
            Arguments.of(null, "test_type", "test_signature"),
            Arguments.of(owner.getKey(), null, "test_signature"),
            Arguments.of(owner.getKey(), "test_type", null)
        );
    }
}
