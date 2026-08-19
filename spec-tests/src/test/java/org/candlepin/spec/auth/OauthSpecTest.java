/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Users;
import org.candlepin.spec.bootstrap.data.util.OAuthUtil;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.util.Map;

// TODO: FIXME: Some of these tests have questionable value and don't really seem to test or validate much of
// anything. They should be pruned or updated as time permits.

@SpecTest
public class OauthSpecTest {

    @Test
    public void shouldReturnsAUnauthorizedIfOauthUserIsNotConfigured() {
        ApiClient client = ApiClients.oauth("baduser", "badsecret");
        assertUnauthorized(() -> client.users().listUsers());
    }

    @Test
    public void shouldReturnsAUnauthorizedIfOauthSecretDoesNotMatch() {
        ApiClient client = ApiClients.oauth(OAuthUtil.CONSUMER_KEY, "badsecret");
        assertUnauthorized(() -> client.users().listUsers());
    }

    @Test
    public void shouldLetACallerActAsAUser() {
        ApiClient adminClient = ApiClients.admin();
        UserDTO user = adminClient.users().createUser(Users.random());
        ApiClient oauthClient = ApiClients.oauthUser(
            OAuthUtil.CONSUMER_KEY, OAuthUtil.CONSUMER_SECRET, user.getUsername());
        UserDTO userInfo = oauthClient.users().getUserInfo(user.getUsername());
        assertThat(userInfo)
            .isEqualTo(user);
    }

    @Test
    public void shouldLetACallerActAsAConsumer() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient oauthClient = ApiClients.oauthConsumer(
            OAuthUtil.CONSUMER_KEY, OAuthUtil.CONSUMER_SECRET, consumer.getUuid());
        ConsumerDTO consumerFromServer = oauthClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumerFromServer)
            .isNotNull()
            .returns(consumer.getId(), ConsumerDTO::getId);
    }

    @Test
    public void shouldReturnsUnauthorizedIfAnUnknownConsumerIsRequested() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient oauthClient = ApiClients.oauthConsumer(
            OAuthUtil.CONSUMER_KEY, OAuthUtil.CONSUMER_SECRET, "some unknown consumer");
        assertUnauthorized(() -> oauthClient.consumers().getConsumer(consumer.getUuid()));
    }

    @Test
    public void shouldFallsBackToTrustedSystemAuthIfNoHeadersAreSet() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ApiClient oauthClient = ApiClients.oauth(OAuthUtil.CONSUMER_KEY, OAuthUtil.CONSUMER_SECRET);
        OwnerDTO ownerFromServer = oauthClient.owners().getOwner(owner.getKey());
        assertThat(ownerFromServer)
            .isEqualTo(owner);
    }

    // These tests verify that the oauth interceptor still functions as expected with various types of
    // requests and inputs. Because our target is the interceptor and not the underlying request itself,
    // these tests are free to include any arbitrary query params or body, even if the target endpoint
    // won't recognize them or otherwise do anything with the data. So long as the endpoint returns a
    // success, we know the oauth interceptor properly signed and cleared the request.
    @Nested
    @TestInstance(Lifecycle.PER_CLASS)
    public class OAuthRequestTests {

        private ApiClient adminClient;
        private ApiClient oauthClient;
        private UserDTO clientUserDto;

        @BeforeAll
        public void setup() throws Exception {
            this.adminClient = ApiClients.admin();
            this.clientUserDto = adminClient.users().createUser(new UserDTO()
                .username(StringUtil.random("oauth_superadmin_user"))
                .password("password")
                .superAdmin(true));

            this.oauthClient = ApiClients.oauthUser(OAuthUtil.CONSUMER_KEY, OAuthUtil.CONSUMER_SECRET,
                this.clientUserDto.getUsername());
        }

        @Test
        public void shouldAllowSimpleDeleteRequests() {
            OwnerDTO owner = this.adminClient.owners()
                .createOwner(Owners.random());

            Response response = Request.from(this.oauthClient)
                .setMethod(Request.Method.DELETE)
                .setPath("/owners/{owner_key}")
                .setPathParam("owner_key", owner.getKey())
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(true, Response::wasSuccessful);
        }

        @Test
        public void shouldAllowDeleteRequestsWithQueryParams() {
            OwnerDTO owner = this.adminClient.owners()
                .createOwner(Owners.random());

            Response response = Request.from(this.oauthClient)
                .setMethod(Request.Method.DELETE)
                .setPath("/owners/{owner_key}")
                .setPathParam("owner_key", owner.getKey())
                .addQueryParam("p1", "v1")
                .addQueryParam("p2", "v2")
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(true, Response::wasSuccessful);
        }

        @Test
        public void shouldAllowSimpleGetRequests() throws Exception {
            OwnerDTO owner = this.adminClient.owners()
                .createOwner(Owners.random());

            Response response = Request.from(this.oauthClient)
                .setMethod(Request.Method.GET)
                .setPath("/owners/{owner_key}")
                .setPathParam("owner_key", owner.getKey())
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(true, Response::wasSuccessful);
        }

        @Test
        public void shouldAllowGetRequestsWithQueryParams() throws Exception {
            OwnerDTO owner = this.adminClient.owners()
                .createOwner(Owners.random());

            Response response = Request.from(this.oauthClient)
                .setMethod(Request.Method.GET)
                .setPath("/owners/{owner_key}")
                .setPathParam("owner_key", owner.getKey())
                .addQueryParam("p1", "v1")
                .addQueryParam("p2", "v2")
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(true, Response::wasSuccessful);
        }

        @Test
        public void shouldAllowSimpleHeadRequests() throws Exception {
            OwnerDTO owner = this.adminClient.owners()
                .createOwner(Owners.random());

            ConsumerDTO consumer = this.adminClient.consumers()
                .createConsumer(Consumers.random(owner));

            Response response = Request.from(this.oauthClient)
                .setMethod(Request.Method.HEAD)
                .setPath("/consumers/{consumer_uuid}/exists")
                .setPathParam("consumer_uuid", consumer.getUuid())
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(true, Response::wasSuccessful);
        }

        @Test
        public void shouldAllowHeadRequestsWithQueryParams() throws Exception {
            OwnerDTO owner = this.adminClient.owners()
                .createOwner(Owners.random());

            ConsumerDTO consumer = this.adminClient.consumers()
                .createConsumer(Consumers.random(owner));

            Response response = Request.from(this.oauthClient)
                .setMethod(Request.Method.HEAD)
                .setPath("/consumers/{consumer_uuid}/exists")
                .setPathParam("consumer_uuid", consumer.getUuid())
                .addQueryParam("p1", "v1")
                .addQueryParam("p2", "v2")
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(true, Response::wasSuccessful);
        }

        @Test
        public void shouldAllowSimplePostRequests() throws Exception {
            OwnerDTO owner = this.adminClient.owners()
                .createOwner(Owners.random());

            ConsumerDTO consumer = this.adminClient.consumers()
                .createConsumer(Consumers.random(owner));

            Response response = Request.from(this.oauthClient)
                .setMethod(Request.Method.POST)
                .setPath("/consumers/{consumer_uuid}")
                .setPathParam("consumer_uuid", consumer.getUuid())
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(true, Response::wasSuccessful);
        }

        @Test
        public void shouldAllowPostRequestsWithQueryParams() throws Exception {
            OwnerDTO owner = this.adminClient.owners()
                .createOwner(Owners.random());

            ConsumerDTO consumer = this.adminClient.consumers()
                .createConsumer(Consumers.random(owner));

            Response response = Request.from(this.oauthClient)
                .setMethod(Request.Method.POST)
                .setPath("/consumers/{consumer_uuid}")
                .setPathParam("consumer_uuid", consumer.getUuid())
                .addQueryParam("p1", "v1")
                .addQueryParam("p2", "v2")
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(true, Response::wasSuccessful);
        }

        @Test
        public void shouldAllowPostRequestsWithRequestBody() throws Exception {
            OwnerDTO owner = this.adminClient.owners()
                .createOwner(Owners.random());

            ConsumerDTO consumer = this.adminClient.consumers()
                .createConsumer(Consumers.random(owner));

            Response response = Request.from(this.oauthClient)
                .setMethod(Request.Method.POST)
                .setPath("/consumers/{consumer_uuid}")
                .setPathParam("consumer_uuid", consumer.getUuid())
                .setBody(Map.of("b1", "v1", "b2", "v2"))
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(true, Response::wasSuccessful);
        }

        @Test
        public void shouldAllowPostRequestsWithRequestBodyAndQueryParams() throws Exception {
            OwnerDTO owner = this.adminClient.owners()
                .createOwner(Owners.random());

            ConsumerDTO consumer = this.adminClient.consumers()
                .createConsumer(Consumers.random(owner));

            Response response = Request.from(this.oauthClient)
                .setMethod(Request.Method.POST)
                .setPath("/consumers/{consumer_uuid}")
                .setPathParam("consumer_uuid", consumer.getUuid())
                .addQueryParam("p1", "v1")
                .addQueryParam("p2", "v2")
                .setBody(Map.of("b1", "v1", "b2", "v2"))
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(true, Response::wasSuccessful);
        }

        @Test
        public void shouldAllowSimplePutRequests() throws Exception {
            OwnerDTO owner = this.adminClient.owners()
                .createOwner(Owners.random());

            ConsumerDTO consumer = this.adminClient.consumers()
                .createConsumer(Consumers.random(owner));

            Response response = Request.from(this.oauthClient)
                .setMethod(Request.Method.PUT)
                .setPath("/consumers/{consumer_uuid}/certificates")
                .setPathParam("consumer_uuid", consumer.getUuid())
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(true, Response::wasSuccessful);
        }

        @Test
        public void shouldAllowPutRequestsWithQueryParams() throws Exception {
            OwnerDTO owner = this.adminClient.owners()
                .createOwner(Owners.random());

            ConsumerDTO consumer = this.adminClient.consumers()
                .createConsumer(Consumers.random(owner));

            Response response = Request.from(this.oauthClient)
                .setMethod(Request.Method.PUT)
                .setPath("/consumers/{consumer_uuid}/certificates")
                .setPathParam("consumer_uuid", consumer.getUuid())
                .addQueryParam("p1", "v1")
                .addQueryParam("p2", "v2")
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(true, Response::wasSuccessful);
        }

        @Test
        public void shouldAllowPutRequestsWithRequestBody() throws Exception {
            OwnerDTO owner = this.adminClient.owners()
                .createOwner(Owners.random());

            ConsumerDTO consumer = this.adminClient.consumers()
                .createConsumer(Consumers.random(owner));

            Response response = Request.from(this.oauthClient)
                .setMethod(Request.Method.PUT)
                .setPath("/consumers/{consumer_uuid}/certificates")
                .setPathParam("consumer_uuid", consumer.getUuid())
                .setBody(Map.of("b1", "v1", "b2", "v2"))
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(true, Response::wasSuccessful);
        }

        @Test
        public void shouldAllowPutRequestsWithRequestBodyAndQueryParams() throws Exception {
            OwnerDTO owner = this.adminClient.owners()
                .createOwner(Owners.random());

            ConsumerDTO consumer = this.adminClient.consumers()
                .createConsumer(Consumers.random(owner));

            Response response = Request.from(this.oauthClient)
                .setMethod(Request.Method.PUT)
                .setPath("/consumers/{consumer_uuid}/certificates")
                .setPathParam("consumer_uuid", consumer.getUuid())
                .addQueryParam("p1", "v1")
                .addQueryParam("p2", "v2")
                .setBody(Map.of("b1", "v1", "b2", "v2"))
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(true, Response::wasSuccessful);
        }
    }
}
