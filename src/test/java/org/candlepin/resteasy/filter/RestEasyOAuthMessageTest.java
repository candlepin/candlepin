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
package org.candlepin.resteasy.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.test.TestUtil;

import net.oauth.OAuth.Parameter;

import org.jboss.resteasy.mock.MockHttpRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.ws.rs.core.MediaType;

public class RestEasyOAuthMessageTest {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String URL = "http://localhost/candlepin/status";

    private static Set<String> httpMethods() {
        return Set.of("DELETE", "GET", "HEAD", "PATCH", "POST", "PUT");
    }

    @ParameterizedTest
    @MethodSource("httpMethods")
    public void testGetParametersWithNullHTTPMethod(String verb) throws Exception {
        MockHttpRequest request = MockHttpRequest.create(verb, URL);
        request.contentType(MediaType.valueOf(MediaType.APPLICATION_FORM_URLENCODED));
        request.header(AUTHORIZATION_HEADER, "OAuth oauth_consumer_key=\"testing\", oauth_version=\"1.0\"");

        // Form parameters that should not be returned
        request.addFormHeader(TestUtil.randomString("key-"), TestUtil.randomString("value-"));
        request.addFormHeader(TestUtil.randomString("key-"), TestUtil.randomString("value-"));

        request.setHttpMethod(null);

        List<Parameter> actual = RestEasyOAuthMessage.getParameters(request);

        assertThat(actual)
            .isNotNull()
            .hasSize(2)
            .contains(new Parameter("oauth_consumer_key", "testing"))
            .contains(new Parameter("oauth_version", "1.0"));
    }

    @ParameterizedTest
    @MethodSource("httpMethods")
    public void testGetParametersWithNullContentType(String verb) throws Exception {
        MockHttpRequest request = MockHttpRequest.create(verb, URL);
        request.header(AUTHORIZATION_HEADER, "OAuth oauth_consumer_key=\"testing\", oauth_version=\"1.0\"");

        // Form parameters that should not be returned
        request.addFormHeader(TestUtil.randomString("key-"), TestUtil.randomString("value-"));
        request.addFormHeader(TestUtil.randomString("key-"), TestUtil.randomString("value-"));

        request.contentType((MediaType) null);

        List<Parameter> actual = RestEasyOAuthMessage.getParameters(request);

        assertThat(actual)
            .isNotNull()
            .hasSize(2)
            .contains(new Parameter("oauth_consumer_key", "testing"))
            .contains(new Parameter("oauth_version", "1.0"));
    }

    @ParameterizedTest
    @MethodSource("httpMethods")
    public void testGetParameters(String verb) throws Exception {
        MockHttpRequest request = MockHttpRequest.create(verb, URL);
        request.contentType(MediaType.valueOf(MediaType.APPLICATION_FORM_URLENCODED));

        List<Parameter> expectedParams = new ArrayList<>();
        expectedParams.add(new Parameter(TestUtil.randomString("key-"), TestUtil.randomString("value-")));
        expectedParams.add(new Parameter(TestUtil.randomString("key-"), TestUtil.randomString("value-")));
        expectedParams.add(new Parameter(TestUtil.randomString("key-"), TestUtil.randomString("value-")));
        expectedParams.add(new Parameter(TestUtil.randomString("key-"), TestUtil.randomString("value-")));

        for (Parameter parameter : expectedParams) {
            request.addFormHeader(parameter.getKey(), parameter.getValue());
        }

        // Add Authorization header and OAuth parameters. Realm parameter should not be returned.
        String authValue = "OAuth realm=\"realm\", oauth_consumer_key=\"key\", oauth_version=\"2.0\"";
        request.header(AUTHORIZATION_HEADER, authValue);
        expectedParams.add(new Parameter("oauth_consumer_key", "key"));
        expectedParams.add(new Parameter("oauth_version", "2.0"));

        List<Parameter> actual = RestEasyOAuthMessage.getParameters(request);

        assertThat(actual)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(expectedParams);
    }

    @ParameterizedTest
    @MethodSource("httpMethods")
    public void testGetParametersWithIncompatibleMediaType(String verb) throws Exception {
        MockHttpRequest request = MockHttpRequest.create(verb, URL);

        // Set the Incompatible content type
        request.contentType(MediaType.valueOf(MediaType.APPLICATION_JSON));

        String authValue = "OAuth oauth_consumer_key=\"testing\", oauth_version=\"1.0\"";
        request.header(AUTHORIZATION_HEADER, authValue);

        // Form parameters that should not be returned
        request.addFormHeader(TestUtil.randomString("key-"), TestUtil.randomString("value-"));
        request.addFormHeader(TestUtil.randomString("key-"), TestUtil.randomString("value-"));

        List<Parameter> actual = RestEasyOAuthMessage.getParameters(request);

        assertThat(actual)
            .isNotNull()
            .hasSize(2)
            .contains(new Parameter("oauth_consumer_key", "testing"))
            .contains(new Parameter("oauth_version", "1.0"));
    }

}

