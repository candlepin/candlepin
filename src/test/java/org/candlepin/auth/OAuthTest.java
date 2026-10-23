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
package org.candlepin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.config.Configuration;
import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.NotAuthorizedException;
import org.candlepin.resteasy.filter.RestEasyOAuthMessage;

import net.oauth.OAuth.Parameter;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;
import net.oauth.signature.OAuthSignatureMethod;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.plugins.server.servlet.HttpServletInputMessage;
import org.jboss.resteasy.specimpl.ResteasyHttpHeaders;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

@ExtendWith(MockitoExtension.class)
public class OAuthTest {

    private static final String URL = "https://localhost:8443/candlepin/owners";
    private static final String CONSUMER_KEY = "oauth-test";
    private static final String CONSUMER_SECRET = "oauth-test-secret";

    @Mock
    private TrustedUserAuth userAuth;
    @Mock
    private TrustedConsumerAuth consumerAuth;
    @Mock
    private TrustedExternalSystemAuth systemAuth;

    private OAuth auth;

    @BeforeEach
    void setUp() {
        Configuration config = mock(Configuration.class);
        when(config.getValuesByPrefix("candlepin.auth.oauth.consumer."))
            .thenReturn(Map.of("candlepin.auth.oauth.consumer." + CONSUMER_KEY + ".secret", CONSUMER_SECRET));
        I18n i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
        this.auth = new OAuth(this.consumerAuth, this.userAuth, this.systemAuth, () -> i18n, config);
        ResteasyContext.addContextDataLevel();
    }

    @AfterEach
    void tearDown() {
        ResteasyContext.removeContextDataLevel();
    }

    private static Stream<String> httpMethods() {
        return Stream.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE", "CONNECT");
    }

    private static Stream<String> nonFormContentTypes() {
        return Stream.of(null, "application/json", "application/json; charset=UTF-8", "text/plain",
            "application/xml", "application/octet-stream", "multipart/form-data; boundary=oauth-test");
    }

    private static Stream<String> formContentTypes() {
        return Stream.of("application/x-www-form-urlencoded", "application/x-www-form-urlencoded; charset=UTF-8");
    }

    private static Stream<Arguments> requestVariants() {
        return OAuthTest.httpMethods().flatMap(method ->
            Stream.concat(OAuthTest.nonFormContentTypes(), OAuthTest.formContentTypes())
                .map(contentType -> Arguments.of(method, contentType)));
    }

    private static Stream<Arguments> nonFormRequestVariants() {
        return OAuthTest.httpMethods().flatMap(method -> OAuthTest.nonFormContentTypes()
            .map(contentType -> Arguments.of(method, contentType)));
    }

    private static Stream<Arguments> formRequestVariants() {
        return OAuthTest.httpMethods().flatMap(method -> OAuthTest.formContentTypes()
            .map(contentType -> Arguments.of(method, contentType)));
    }

    private static Stream<Arguments> servletFormRequestVariants() {
        return Stream.of("PUT", "PATCH", "DELETE").flatMap(method -> OAuthTest.formContentTypes()
            .map(contentType -> Arguments.of(method, contentType)));
    }

    private static Stream<Arguments> previouslyReadServletFormVariants() {
        return OAuthTest.httpMethods().flatMap(method -> OAuthTest.formContentTypes()
            .flatMap(contentType -> Stream.of("servlet", "decoded", "encoded", "body")
                .map(reader -> Arguments.of(method, contentType, reader))));
    }

    private static Stream<Arguments> servletOAuthFormVariants() {
        return OAuthTest.httpMethods().flatMap(method -> OAuthTest.formContentTypes()
            .flatMap(contentType -> Stream.of("unread", "servlet", "decoded", "encoded", "body")
                .map(reader -> Arguments.of(method, contentType, reader))));
    }

    @ParameterizedTest
    @MethodSource("requestVariants")
    public void testAuthenticatesEmptyRequests(String method, String contentType) throws Exception {
        MockHttpRequest request = this.request(method, URL, contentType, "");
        this.authorize(request, this.message(method, URL, List.of()));

        this.assertSystemPrincipal(request);
    }

    @ParameterizedTest
    @MethodSource("requestVariants")
    public void testAuthenticatesRequestsWithQueryParameters(String method, String contentType) throws Exception {
        String url = URL + "?name=hello+world&name=a%2Bb&empty=&symbol=%26%3D%25&unicode=%C4%8D";
        MockHttpRequest request = this.request(method, url, contentType, "");
        this.authorize(request, this.message(method, url, List.of()));

        this.assertSystemPrincipal(request);
    }

    @ParameterizedTest
    @MethodSource("requestVariants")
    public void testAuthenticatesOAuthParametersInUri(String method, String contentType) throws Exception {
        OAuthMessage message = this.message(method, URL + "?name=value", List.of());
        this.sign(message, CONSUMER_SECRET);
        String url = message.URL + "&" + net.oauth.OAuth.formEncode(message.getParameters());
        MockHttpRequest request = this.request(method, url, contentType, "");

        this.assertSystemPrincipal(request);
    }

    @ParameterizedTest
    @MethodSource("formRequestVariants")
    public void testAuthenticatesOAuthParametersInFormBody(String method, String contentType) throws Exception {
        String url = URL + "?name=shared";
        List<Parameter> parameters = List.of(new Parameter("name", "shared"), new Parameter("name", "a+b"),
            new Parameter("empty", ""), new Parameter("symbols", "&=%"), new Parameter("unicode", "č"));
        OAuthMessage message = this.message(method, url, parameters);
        this.sign(message, CONSUMER_SECRET);
        String body = net.oauth.OAuth.formEncode(message.getParameters())
            .replace("oauth_consumer_key=", "oauth%5Fconsumer%5Fkey=");
        MockHttpRequest request = this.request(method, url, contentType, body);

        this.assertSystemPrincipal(request);
        assertThat(request.getDecodedFormParameters().get("oauth_consumer_key")).containsExactly(CONSUMER_KEY);
        assertThat(request.getDecodedFormParameters().get("name")).containsExactly("shared", "a+b");
        assertThat(request.getDecodedFormParameters().get("empty")).containsExactly("");
    }

    @ParameterizedTest
    @MethodSource("servletOAuthFormVariants")
    public void testAuthenticatesServletOAuthParametersInFormBody(String method, String contentType,
        String reader) throws Exception {

        String url = URL + "?name=shared";
        OAuthMessage message = this.message(method, url,
            List.of(new Parameter("name", "shared"), new Parameter("empty", "")));
        this.sign(message, CONSUMER_SECRET);
        Map<String, String[]> servletParameters = new HashMap<>();
        if (method.equals("POST")) {
            for (Map.Entry<String, String> parameter : message.getParameters()) {
                servletParameters.put(parameter.getKey(), new String[] {parameter.getValue()});
            }
            servletParameters.put("name", new String[] {"shared", "shared"});
        }
        else {
            servletParameters.put("name", new String[] {"shared"});
        }
        HttpRequest request = this.servletRequest(method, url, contentType,
            net.oauth.OAuth.formEncode(message.getParameters()), servletParameters);
        if (!reader.equals("unread")) {
            this.readFormParameters(request, reader);
        }

        this.assertSystemPrincipal(request);
        assertThat(request.getDecodedFormParameters().get("oauth_consumer_key")).containsExactly(CONSUMER_KEY);
        assertThat(request.getDecodedFormParameters().get("name")).containsExactly("shared");
        assertThat(request.getDecodedFormParameters().get("empty")).containsExactly("");
    }

    @ParameterizedTest
    @MethodSource("formRequestVariants")
    public void testRejectsIncorrectSecretInFormBody(String method, String contentType) throws Exception {
        OAuthMessage message = this.message(method, URL, List.of());
        this.sign(message, "incorrect-secret");
        MockHttpRequest request = this.request(method, URL, contentType,
            net.oauth.OAuth.formEncode(message.getParameters()));

        this.assertUnauthorized(request);
    }

    @ParameterizedTest
    @MethodSource("formRequestVariants")
    public void testRejectsTamperedBodyOnlyOAuthRequest(String method, String contentType) throws Exception {
        OAuthMessage message = this.message(method, URL, List.of(new Parameter("name", "original")));
        this.sign(message, CONSUMER_SECRET);
        String body = net.oauth.OAuth.formEncode(message.getParameters()).replace("name=original", "name=changed");
        MockHttpRequest request = this.request(method, URL, contentType, body);

        this.assertUnauthorized(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"oauth_nonce", "oauth_timestamp", "oauth_signature_method", "oauth_signature"})
    public void testRejectsIncompleteOAuthParametersInFormBody(String parameter) throws Exception {
        OAuthMessage message = this.messageWithoutParameter("POST", parameter);
        String body = net.oauth.OAuth.formEncode(message.getParameters());
        MockHttpRequest request = this.request("POST", URL, MediaType.APPLICATION_FORM_URLENCODED, body);

        this.assertMissingParameter(request);
    }

    @ParameterizedTest
    @MethodSource("nonFormRequestVariants")
    public void testIgnoresOAuthCredentialsInNonFormBodies(String method, String contentType) throws Exception {
        String body = "oauth_consumer_key=" + CONSUMER_KEY + "&oauth_signature=untrusted";
        MockHttpRequest request = this.request(method, URL, contentType, body);

        assertThat(this.auth.getPrincipal(request)).isNull();
        assertThat(request.getInputStream().readAllBytes()).isEqualTo(body.getBytes(StandardCharsets.UTF_8));
        verifyNoInteractions(this.userAuth, this.consumerAuth, this.systemAuth);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "name=value", "name=oauth_consumer_key&empty=", "oauth=value"})
    public void testIgnoresFormBodiesWithoutOAuthCredentials(String body) throws Exception {
        MockHttpRequest request = this.request("POST", URL, MediaType.APPLICATION_FORM_URLENCODED, body);

        assertThat(this.auth.getPrincipal(request)).isNull();
        assertThat(RestEasyOAuthMessage.getParameters(request)).containsExactlyInAnyOrderElementsOf(
            net.oauth.OAuth.decodeForm(body));
        verifyNoInteractions(this.userAuth, this.consumerAuth, this.systemAuth);
    }

    @ParameterizedTest
    @MethodSource("nonFormRequestVariants")
    public void testLeavesNonFormBodiesAvailable(String method, String contentType) throws Exception {
        String body = "name=value&oauth_consumer_key=untrusted";
        MockHttpRequest request = this.request(method, URL, contentType, body);
        this.authorize(request, this.message(method, URL, List.of()));

        this.assertSystemPrincipal(request);
        assertThat(request.getInputStream().readAllBytes()).isEqualTo(body.getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @MethodSource("formRequestVariants")
    public void testAuthenticatesFormBodiesAndQueryParameters(String method, String contentType) throws Exception {
        List<Parameter> parameters = List.of(new Parameter("name", "hello world"), new Parameter("name", "a+b"),
            new Parameter("empty", ""), new Parameter("symbols", "&=%"), new Parameter("unicode", "č"));
        String url = URL + "?name=query&empty=";
        MockHttpRequest request = this.request(method, url, contentType, net.oauth.OAuth.formEncode(parameters));
        this.authorize(request, this.message(method, url, parameters));

        this.assertSystemPrincipal(request);
        assertThat(request.getDecodedFormParameters().get("name")).containsExactly("hello world", "a+b");
    }

    @ParameterizedTest
    @MethodSource("formContentTypes")
    public void testAuthenticatesServletPostWithSharedQueryAndFormParameters(String contentType) throws Exception {
        String url = URL + "?name=shared&empty=";
        List<Parameter> parameters = List.of(new Parameter("name", "shared"), new Parameter("name", "a+b"),
            new Parameter("empty", ""));
        // Tomcat combines decoded query and POST body parameters, preserving repeated values.
        Map<String, String[]> servletParameters = Map.of(
            "name", new String[] {"shared", "shared", "a+b"}, "empty", new String[] {"", ""});
        HttpRequest request = this.servletRequest("POST", url, contentType,
            net.oauth.OAuth.formEncode(parameters), servletParameters);
        this.authorize(request, this.message("POST", url, parameters));

        this.assertSystemPrincipal(request);
        assertThat(request.getDecodedFormParameters().get("name")).containsExactly("shared", "a+b");
        assertThat(request.getDecodedFormParameters().get("empty")).containsExactly("");
    }

    @ParameterizedTest
    @MethodSource("formContentTypes")
    public void testAuthenticatesServletPostAfterServletParametersRead(String contentType) throws Exception {
        String url = URL + "?name=query";
        List<Parameter> parameters = List.of(new Parameter("name", "body"));
        HttpRequest request = this.servletRequest("POST", url, contentType,
            net.oauth.OAuth.formEncode(parameters), Map.of("name", new String[] {"query", "body"}));
        // Servlet parsing consumes the POST body without populating RESTEasy's form caches.
        this.readFormParameters(request, "servlet");
        this.authorize(request, this.message("POST", url, parameters));

        this.assertSystemPrincipal(request);
        assertThat(request.getDecodedFormParameters().get("name")).containsExactly("body");
    }

    @ParameterizedTest
    @MethodSource("previouslyReadServletFormVariants")
    public void testAuthenticatesPreviouslyReadServletFormParameters(String method, String contentType,
        String reader) throws Exception {

        String url = URL + "?name=shared&name=shared&empty=";
        List<Parameter> parameters = List.of(new Parameter("name", "shared"), new Parameter("name", "shared"),
            new Parameter("name", "a+b"), new Parameter("empty", ""), new Parameter("empty", ""));
        Map<String, String[]> servletParameters = Map.of(
            "name", new String[] {"shared", "shared", "shared", "shared", "a+b"},
            "empty", new String[] {"", "", ""});
        if (!method.equals("POST")) {
            servletParameters = Map.of("name", new String[] {"shared", "shared"}, "empty", new String[] {""});
        }
        HttpRequest request = this.servletRequest(method, url, contentType,
            net.oauth.OAuth.formEncode(parameters), servletParameters);
        this.readFormParameters(request, reader);
        this.authorize(request, this.message(method, url, parameters));

        this.assertSystemPrincipal(request);
        assertThat(request.getDecodedFormParameters().get("name")).containsExactly("shared", "shared", "a+b");
        assertThat(request.getDecodedFormParameters().get("empty")).containsExactly("", "");
        assertThat(request.getFormParameters().get("name")).containsExactly("shared", "shared", "a%2Bb");

        // Repeated extraction must preserve the same multiplicity without consuming form data again.
        List<Parameter> expected = new ArrayList<>(parameters);
        expected.addAll(List.of(new Parameter("name", "shared"), new Parameter("name", "shared"),
            new Parameter("empty", "")));
        assertThat(RestEasyOAuthMessage.getParameters(request).stream()
            .filter(parameter -> !parameter.getKey().startsWith("oauth_")))
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @ParameterizedTest
    @MethodSource("previouslyReadServletFormVariants")
    public void testRejectsUnsignedPreviouslyReadServletFormParameters(String method, String contentType,
        String reader) throws Exception {

        String url = URL + "?name=shared&empty=";
        Map<String, String[]> servletParameters = method.equals("POST") ?
            Map.of("name", new String[] {"shared", "shared"}, "empty", new String[] {"", ""}) :
            Map.of("name", new String[] {"shared"}, "empty", new String[] {""});
        HttpRequest request = this.servletRequest(method, url, contentType, "name=shared&empty=", servletParameters);
        this.readFormParameters(request, reader);
        this.authorize(request, this.message(method, url, List.of()));

        this.assertUnauthorized(request);
    }

    @ParameterizedTest
    @MethodSource("previouslyReadServletFormVariants")
    public void testAuthenticatesPreviouslyReadServletQueryOnlyRequests(String method, String contentType,
        String reader) throws Exception {

        String url = URL + "?name=shared&name=shared&empty=";
        HttpRequest request = this.servletRequest(method, url, contentType, "",
            Map.of("name", new String[] {"shared", "shared"}, "empty", new String[] {""}));
        this.readFormParameters(request, reader);
        this.authorize(request, this.message(method, url, List.of()));

        this.assertSystemPrincipal(request);
        assertThat(request.getDecodedFormParameters()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("formContentTypes")
    public void testAuthenticatesPreviouslyReadServletFormWithOAuthParametersInUri(String contentType)
        throws Exception {

        String url = URL + "?name=shared";
        List<Parameter> body = List.of(new Parameter("name", "shared"));
        OAuthMessage message = this.message("POST", url, body);
        this.sign(message, CONSUMER_SECRET);
        List<Map.Entry<String, String>> credentials = message.getParameters().stream()
            .filter(parameter -> parameter.getKey().startsWith("oauth_"))
            .toList();
        url += "&" + net.oauth.OAuth.formEncode(credentials);
        Map<String, String[]> servletParameters = new HashMap<>();
        servletParameters.put("name", new String[] {"shared", "shared"});
        for (Map.Entry<String, String> credential : credentials) {
            servletParameters.put(credential.getKey(), new String[] {credential.getValue()});
        }
        HttpRequest request = this.servletRequest("POST", url, contentType,
            net.oauth.OAuth.formEncode(body), servletParameters);
        request.getDecodedFormParameters();

        this.assertSystemPrincipal(request);
        assertThat(request.getDecodedFormParameters()).containsOnlyKeys("name");
        assertThat(request.getDecodedFormParameters().get("name")).containsExactly("shared");
    }

    @ParameterizedTest
    @MethodSource("servletFormRequestVariants")
    public void testAuthenticatesServletFormBodies(String method, String contentType) throws Exception {
        String url = URL + "?query=value";
        List<Parameter> parameters = List.of(new Parameter("name", "hello world"), new Parameter("name", "a+b"),
            new Parameter("empty", ""), new Parameter("symbols", "&=%"), new Parameter("unicode", "č"));
        // With Tomcat's default configuration, non-POST parameter maps contain only query parameters.
        HttpRequest request = this.servletRequest(method, url, contentType,
            net.oauth.OAuth.formEncode(parameters), Map.of("query", new String[] {"value"}));
        this.authorize(request, this.message(method, url, parameters));

        this.assertSystemPrincipal(request);
        assertThat(request.getDecodedFormParameters().get("name")).containsExactly("hello world", "a+b");
    }

    @ParameterizedTest
    @MethodSource("servletFormRequestVariants")
    public void testRejectsUnsignedServletFormBodies(String method, String contentType) throws Exception {
        String url = URL + "?query=value";
        HttpRequest request = this.servletRequest(method, url, contentType, "name=unsigned",
            Map.of("query", new String[] {"value"}));
        this.authorize(request, this.message(method, url, List.of()));

        this.assertUnauthorized(request);
    }

    @ParameterizedTest
    @MethodSource("formRequestVariants")
    public void testRejectsTamperedFormBodies(String method, String contentType) throws Exception {
        MockHttpRequest request = this.request(method, URL, contentType, "name=changed");
        this.authorize(request, this.message(method, URL, List.of(new Parameter("name", "original"))));

        this.assertUnauthorized(request);
    }

    @ParameterizedTest
    @MethodSource("requestVariants")
    public void testRejectsIncorrectSecret(String method, String contentType) throws Exception {
        MockHttpRequest request = this.request(method, URL, contentType, "");
        OAuthMessage message = this.message(method, URL, List.of());
        this.sign(message, "incorrect-secret");
        request.header(HttpHeaders.AUTHORIZATION, message.getAuthorizationHeader(null));

        this.assertUnauthorized(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"method", "path", "query"})
    public void testRejectsTamperedSignedRequest(String component) throws Exception {
        OAuthMessage message = this.message("POST", URL + "?name=original", List.of());
        String method = component.equals("method") ? "PUT" : "POST";
        String url = switch (component) {
            case "path" -> URL + "/changed?name=original";
            case "query" -> URL + "?name=changed";
            default -> message.URL;
        };
        MockHttpRequest request = this.request(method, url, MediaType.APPLICATION_JSON, "{}");
        this.authorize(request, message);

        this.assertUnauthorized(request);
    }

    @Test
    public void testRejectsUnknownConsumer() throws Exception {
        OAuthMessage message = new OAuthMessage("GET", URL, this.message("GET", URL, List.of()).getParameters()
            .stream()
            .filter(parameter -> !parameter.getKey().equals("oauth_consumer_key"))
            .toList());
        message.addParameter("oauth_consumer_key", "unknown-consumer");
        MockHttpRequest request = this.request("GET", URL, null, "");
        this.authorize(request, message);

        this.assertUnauthorized(request);
    }

    @Test
    public void testRejectsUnknownConsumerBeforeSignatureValidation() throws Exception {
        MockHttpRequest request = this.request("GET", URL, null, "");
        request.header(HttpHeaders.AUTHORIZATION, "OAuth oauth_consumer_key=\"unknown-consumer\"");
        try (MockedConstruction<RestEasyOAuthMessage> messages = mockConstruction(RestEasyOAuthMessage.class,
            (message, context) -> when(message.getConsumerKey()).thenReturn("unknown-consumer"))) {

            this.assertUnauthorized(request);

            OAuthMessage message = messages.constructed().getFirst();
            verify(message).getConsumerKey();
            verifyNoMoreInteractions(message);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"oauth_nonce", "oauth_timestamp", "oauth_signature_method", "oauth_signature"})
    public void testRejectsMissingRequiredParameters(String parameter) throws Exception {
        OAuthMessage incomplete = this.messageWithoutParameter("GET", parameter);
        MockHttpRequest request = this.request("GET", URL, null, "");
        request.header(HttpHeaders.AUTHORIZATION, incomplete.getAuthorizationHeader(null));

        this.assertMissingParameter(request);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testRejectsMissingConsumerKey(boolean formBody) throws Exception {
        OAuthMessage incomplete = this.messageWithoutParameter("POST", "oauth_consumer_key");
        MockHttpRequest request = this.request("POST", URL,
            formBody ? MediaType.APPLICATION_FORM_URLENCODED : null,
            formBody ? net.oauth.OAuth.formEncode(incomplete.getParameters()) : "");
        if (!formBody) {
            request.header(HttpHeaders.AUTHORIZATION, incomplete.getAuthorizationHeader(null));
        }

        // Accessor lookup rejects a missing consumer key before the validator is called.
        this.assertUnauthorized(request);
    }

    @Test
    public void testRejectsExpiredTimestamp() throws Exception {
        OAuthMessage message = this.message("GET", URL, List.of());
        OAuthMessage expired = new OAuthMessage("GET", URL, message.getParameters().stream()
            .filter(entry -> !entry.getKey().equals("oauth_timestamp"))
            .toList());
        expired.addParameter("oauth_timestamp", Long.toString(Instant.now().minusSeconds(3600).getEpochSecond()));
        MockHttpRequest request = this.request("GET", URL, null, "");
        this.authorize(request, expired);

        assertThrows(CandlepinException.class, () -> this.auth.getPrincipal(request));
        verifyNoInteractions(this.userAuth, this.consumerAuth, this.systemAuth);
    }

    @Test
    public void testRejectsReplayedRequest() throws Exception {
        MockHttpRequest request = this.request("GET", URL, null, "");
        this.authorize(request, this.message("GET", URL, List.of()));
        this.assertSystemPrincipal(request);

        assertThrows(CandlepinException.class, () -> this.auth.getPrincipal(request));
        verify(this.systemAuth).getPrincipal(request);
    }

    @Test
    public void testRejectsDuplicateOAuthParametersAcrossHeaderAndUri() throws Exception {
        String url = URL + "?oauth_consumer_key=" + CONSUMER_KEY;
        MockHttpRequest request = this.request("GET", url, null, "");
        this.authorize(request, this.message("GET", url, List.of()));

        assertThrows(CandlepinException.class, () -> this.auth.getPrincipal(request));
        verifyNoInteractions(this.userAuth, this.consumerAuth, this.systemAuth);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Basic dXNlcjpwYXNz", "Bearer token"})
    public void testIgnoresRequestsWithoutOAuthCredentials(String authorization) throws Exception {
        MockHttpRequest request = this.request("GET", URL, null, "");
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }

        assertThat(this.auth.getPrincipal(request)).isNull();
        verifyNoInteractions(this.userAuth, this.consumerAuth, this.systemAuth);
    }

    @Test
    public void testIgnoresNullRequest() {
        assertThat(this.auth.getPrincipal(null)).isNull();
        verifyNoInteractions(this.userAuth, this.consumerAuth, this.systemAuth);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testSelectsUserBeforeConsumer(boolean includeConsumer) throws Exception {
        MockHttpRequest request = this.request("GET", URL, null, "");
        request.header(TrustedUserAuth.USER_HEADER, "test-user");
        if (includeConsumer) {
            request.header(TrustedConsumerAuth.CONSUMER_HEADER, "test-consumer");
        }
        this.authorize(request, this.message("GET", URL, List.of()));
        Principal principal = mock(Principal.class);
        when(this.userAuth.getPrincipal(request)).thenReturn(principal);

        assertThat(this.auth.getPrincipal(request)).isSameAs(principal);
        verify(this.userAuth).getPrincipal(request);
        verifyNoInteractions(this.consumerAuth, this.systemAuth);
    }

    @Test
    public void testSelectsConsumerWithEmptyUserHeader() throws Exception {
        MockHttpRequest request = this.request("GET", URL, null, "");
        request.header(TrustedUserAuth.USER_HEADER, "");
        request.header(TrustedConsumerAuth.CONSUMER_HEADER, "test-consumer");
        this.authorize(request, this.message("GET", URL, List.of()));
        Principal principal = mock(Principal.class);
        when(this.consumerAuth.getPrincipal(request)).thenReturn(principal);

        assertThat(this.auth.getPrincipal(request)).isSameAs(principal);
        verify(this.consumerAuth).getPrincipal(request);
        verifyNoInteractions(this.userAuth, this.systemAuth);
    }

    private MockHttpRequest request(String method, String url, String contentType, String body) throws Exception {
        MockHttpRequest request = MockHttpRequest.create(method, url);
        request.content(body.getBytes(StandardCharsets.UTF_8));
        if (contentType != null) {
            request.contentType(contentType);
        }
        return request;
    }

    private HttpRequest servletRequest(String method, String url, String contentType, String body,
        Map<String, String[]> servletParameters) throws Exception {

        MockHttpRequest metadata = MockHttpRequest.create(method, url);
        metadata.contentType(contentType);
        ByteArrayInputStream input = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        HttpServletRequest servlet = mock(HttpServletRequest.class);
        ResteasyContext.pushContext(HttpServletRequest.class, servlet);
        AtomicReference<Map<String, String[]>> servletParameterCache = new AtomicReference<>();
        // These model the container's optional parsing path; the adapter may read the body directly instead.
        lenient().when(servlet.getMethod()).thenReturn(method);
        lenient().when(servlet.getParameterMap()).thenAnswer(invocation -> {
            if (servletParameterCache.get() == null) {
                Map<String, String[]> values = servletParameters;
                if (method.equals("POST")) {
                    if (input.available() == 0) {
                        values = metadata.getUri().getQueryParameters().entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey,
                                entry -> entry.getValue().toArray(String[]::new)));
                    }
                    // Parsing a POST body consumes the stream, but keeps the servlet parameter map.
                    input.skipNBytes(input.available());
                }
                servletParameterCache.set(values);
            }
            return servletParameterCache.get();
        });

        HttpServletInputMessage request = new HttpServletInputMessage(servlet, null, null, null,
            (ResteasyHttpHeaders) metadata.getHttpHeaders(), metadata.getUri(), method, null);
        request.setInputStream(input);
        return request;
    }

    private void readFormParameters(HttpRequest request, String reader) {
        switch (reader) {
            case "servlet" -> ResteasyContext.getContextData(HttpServletRequest.class).getParameterMap();
            case "decoded" -> request.getDecodedFormParameters();
            case "encoded" -> request.getFormParameters();
            case "body" -> ((HttpServletInputMessage) request).getPutFormParameters();
            default -> throw new IllegalArgumentException("Unknown form reader: " + reader);
        }
    }

    private OAuthMessage message(String method, String url, List<Parameter> bodyParameters) {
        OAuthMessage message = new OAuthMessage(method, url, bodyParameters);
        message.addParameter("oauth_consumer_key", CONSUMER_KEY);
        message.addParameter("oauth_signature_method", "HMAC-SHA1");
        message.addParameter("oauth_timestamp", Long.toString(Instant.now().getEpochSecond()));
        // The production validator shares its nonce cache across provider instances and test invocations.
        message.addParameter("oauth_nonce", UUID.randomUUID().toString());
        message.addParameter("oauth_version", "1.0");
        return message;
    }

    private void sign(OAuthMessage message, String secret) throws Exception {
        OAuthAccessor accessor = new OAuthAccessor(new OAuthConsumer(null, CONSUMER_KEY, secret, null));
        OAuthSignatureMethod.newMethod("HMAC-SHA1", accessor).sign(message);
    }

    private OAuthMessage messageWithoutParameter(String method, String parameter) throws Exception {
        OAuthMessage message = new OAuthMessage(method, URL, this.message(method, URL, List.of()).getParameters()
            .stream()
            .filter(entry -> !entry.getKey().equals(parameter))
            .toList());
        if (!parameter.equals("oauth_signature")) {
            // Sign the incomplete message so an invalid signature cannot mask a missing-parameter failure.
            this.sign(message, CONSUMER_SECRET);
        }
        return message;
    }

    private void authorize(HttpRequest request, OAuthMessage message) throws Exception {
        this.sign(message, CONSUMER_SECRET);
        request.getMutableHeaders().add(HttpHeaders.AUTHORIZATION, message.getAuthorizationHeader(null));
    }

    private void assertSystemPrincipal(HttpRequest request) {
        Principal principal = new ExternalSystemPrincipal();
        when(this.systemAuth.getPrincipal(request)).thenReturn(principal);

        assertThat(this.auth.getPrincipal(request)).isSameAs(principal);
        verify(this.systemAuth).getPrincipal(request);
        verifyNoInteractions(this.userAuth, this.consumerAuth);
    }

    private void assertUnauthorized(HttpRequest request) {
        NotAuthorizedException exception = assertThrows(NotAuthorizedException.class,
            () -> this.auth.getPrincipal(request));
        assertThat(exception.httpReturnCode()).isEqualTo(Status.UNAUTHORIZED);
        verifyNoInteractions(this.userAuth, this.consumerAuth, this.systemAuth);
    }

    private void assertMissingParameter(HttpRequest request) {
        CandlepinException exception = assertThrows(CandlepinException.class,
            () -> this.auth.getPrincipal(request));
        assertThat(exception).hasMessageEndingWith("parameter_absent");
        verifyNoInteractions(this.userAuth, this.consumerAuth, this.systemAuth);
    }
}
