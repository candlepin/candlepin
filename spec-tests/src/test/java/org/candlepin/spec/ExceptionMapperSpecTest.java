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
package org.candlepin.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SpecTest
public class ExceptionMapperSpecTest {

    @Test
    public void shouldUnknownRouteReturnsSafeNotFoundMessage() {
        Response response = Request.from(ApiClients.admin())
            .setPath("/unknown-route-secret-token")
            .execute();

        ExceptionMapperSpecTest.assertError(response, 404, "Not Found");
        assertThat(response.getHeaders()).containsKey("X-Version");
        assertThat(response.deserialize().get("requestUuid").asString()).isNotBlank();
    }

    @Test
    public void shouldUnsupportedMethodReturnsSafeMessage() {
        Response response = Request.from(ApiClients.admin())
            .setPath("/status")
            .setMethod(Request.Method.POST)
            .execute();

        ExceptionMapperSpecTest.assertError(response, 405, "Method Not Allowed");
    }

    @Test
    public void shouldUnsupportedResponseFormatReturnsSafeMessage() {
        Response response = Request.from(ApiClients.admin())
            .setPath("/status")
            .addHeader("Accept", "text/html")
            .execute();

        ExceptionMapperSpecTest.assertError(response, 406, "Not Acceptable");
    }

    @Test
    public void shouldUnsupportedRequestFormatReturnsSafeMessage() {
        Response response = Request.from(ApiClients.admin())
            .setPath("/owners")
            .setMethod(Request.Method.POST)
            .addHeader("Content-Type", "text/plain")
            .setBody("secret-token")
            .execute();

        ExceptionMapperSpecTest.assertError(response, 415, "Unsupported Media Type");
    }

    @Test
    public void shouldInvalidJsonValueReturnsSafeMessage() {
        Response response = Request.from(ApiClients.admin())
            .setPath("/owners")
            .setMethod(Request.Method.POST)
            .setBody("{\"key\": {\"private\": \"secret-token\"}}")
            .execute();

        ExceptionMapperSpecTest.assertError(response, 400, "Bad Request");
    }

    @Test
    public void shouldOptionsResponsePreservesAllowedMethods() {
        Response response = Request.from(ApiClients.admin())
            .setPath("/status")
            .setMethod(Request.Method.OPTIONS)
            .execute();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeaders().get("Allow")).anySatisfy(value -> assertThat(value).contains("GET"));
    }

    private static void assertError(Response response, int status, String message) {
        assertThat(response.getCode()).isEqualTo(status);
        assertThat(response.deserialize().get("displayMessage").asString()).isEqualTo(message);
        assertThat(response.getBodyAsString()).doesNotContain("secret-token", "internal.example", "Runtime Error",
            "RESTEASY", "org.candlepin", "org.jboss", "ExceptionTestResource");
    }

    @Nested
    @OnlyInHosted
    class FrameworkExceptions {

        @ParameterizedTest
        @CsvSource({
            "application,,500,Internal Server Error",
            "bad-request,,400,Bad Request",
            "conflict,,409,The requested resource already exists",
            "constraint-violation,,400,name: may not be null",
            "failure,,400,Bad Request",
            "failure,500,500,Internal Server Error",
            "internal-server-error,,500,Internal Server Error",
            "ise,,500,An unexpected error occurred",
            "jaxb-marshal,,500,Internal Server Error",
            "jaxb-marshal,400,400,Bad Request",
            "jaxb-unmarshal,,400,Bad Request",
            "jaxb-unmarshal,500,500,Internal Server Error",
            "no-log,,400,Bad Request",
            "no-log,403,403,Forbidden",
            "not-acceptable,,406,Not Acceptable",
            "not-allowed,,405,Method Not Allowed",
            "not-authorized,,401,Unauthorized",
            "not-found,,404,Not Found",
            "not-supported,,415,Unsupported Media Type",
            "options,,500,Internal Server Error",
            "options,400,400,Bad Request",
            "reader,,500,Internal Server Error",
            "reader,400,400,Bad Request",
            "rollback,,400,Bad Request",
            "rollback-no-cause,,400,Bad Request",
            "rollback-validation,,400,Bad Request",
            "rollback-constraint-violation,,400,name: may not be null",
            "runtime,,500,An unexpected error occurred",
            "validation,,400,Bad Request",
            "web-application,,500,Internal Server Error",
            "web-application,403,403,Forbidden",
            "web-application,409,409,Request failed with status code 409",
            "writer,,500,Internal Server Error",
            "writer,400,400,Bad Request",
            "wrapped-ise,,500,An unexpected error occurred"
        })
        void shouldHidesFrameworkExceptionDetails(String type, Integer embeddedStatus, int status, String message) {
            Request request = Request.from(ApiClients.admin())
                .setPath("/hostedtest/exceptions/{type}")
                .setPathParam("type", type);
            if (embeddedStatus != null) {
                request.addQueryParam("status", embeddedStatus.toString());
            }

            ExceptionMapperSpecTest.assertError(request.execute(), status, message);
        }

        @Test
        void shouldReplaceOptionsErrorEntityHeaders() {
            Response response = Request.from(ApiClients.admin())
                .setPath("/hostedtest/exceptions/options-entity-headers")
                .execute();

            ExceptionMapperSpecTest.assertError(response, 500, "Internal Server Error");
            assertThat(response.getHeaders()).doesNotContainKeys("Content-Encoding", "Content-Language", "ETag");
            assertThat(response.getHeaders().get("Content-Type"))
                .anySatisfy(value -> assertThat(value).startsWith("application/json"));
            assertThat(response.getHeaders().get("Allow")).containsExactly("GET, OPTIONS");
            assertThat(response.getHeaders().get("Retry-After")).containsExactly("60");
            if (response.getHeaders().containsKey("Content-Length")) {
                assertThat(response.getHeaders().get("Content-Length"))
                    .containsExactly(Integer.toString(response.getBody().length));
            }
        }

        @Test
        void shouldPreserveServerErrorHeaders() {
            Response response = Request.from(ApiClients.admin())
                .setPath("/hostedtest/exceptions/service-unavailable")
                .execute();

            ExceptionMapperSpecTest.assertError(response, 503, "An unexpected error occurred");
            assertThat(response.getHeaders().get("Retry-After")).containsExactly("60");
            assertThat(response.getHeaders()).containsKey("X-Version");
            assertThat(response.deserialize().get("requestUuid").asString()).isNotBlank();
        }
    }
}
