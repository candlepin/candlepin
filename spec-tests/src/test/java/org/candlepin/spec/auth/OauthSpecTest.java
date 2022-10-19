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

package org.candlepin.spec.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertUnauthorized;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Users;

import org.junit.jupiter.api.Test;

@SpecTest
@OnlyInHosted
public class OauthSpecTest {

    private final String OAUTH_CONSUMER = "rspec";
    private final String OAUTH_SECRET = "rspec-oauth-secret";


    @Test
    void shouldReturnsAUnauthorizedIfOauthUserIsNotConfigured() {
        ApiClient oauth = ApiClients.oauth("baduser", "badsecret");
        assertUnauthorized(() -> oauth.users().listUsers());
    }

    @Test
    void shouldReturnsAUnauthorizedIfOauthSecretDoesNotMatch() {
        ApiClient oauth = ApiClients.oauth(OAUTH_CONSUMER, "badsecret");
        assertUnauthorized(() -> oauth.users().listUsers());
    }

    @Test
    void shouldLetACallerActAsAUser() {
        ApiClient adminClient = ApiClients.admin();
        UserDTO user = adminClient.users().createUser(Users.random());
        ApiClient oauthUser = ApiClients.oauthUser(
            OAUTH_CONSUMER, OAUTH_SECRET, user.getUsername());
        UserDTO userInfo = oauthUser.users().getUserInfo(user.getUsername());
        assertThat(userInfo)
            .isEqualTo(user);
    }

    @Test
    void shouldLetACallerActAsAConsumer() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient oauthConsumer = ApiClients.oauthConsumer(
            OAUTH_CONSUMER, OAUTH_SECRET, consumer.getUuid());
        ConsumerDTO consumerFromServer = oauthConsumer.consumers().getConsumer(consumer.getUuid());
        assertThat(consumerFromServer)
            .isNotNull()
            .returns(consumer.getId(), ConsumerDTO::getId);
    }

    @Test
    void shouldReturnsUnauthorizedIfAnUnknownConsumerIsRequested() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient oauthConsumer = ApiClients.oauthConsumer(
            OAUTH_CONSUMER, OAUTH_SECRET, "some unknown consumer");
        assertUnauthorized(() -> oauthConsumer.consumers().getConsumer(consumer.getUuid()));
    }

    @Test
    void shouldFallsBackToTrustedSystemAuthIfNoHeadersAreSet() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ApiClient oauth = ApiClients.oauth(OAUTH_CONSUMER, OAUTH_SECRET);
        OwnerDTO ownerFromServer = oauth.owners().getOwner(owner.getKey());
        assertThat(ownerFromServer)
            .isEqualTo(owner);
    }
}
