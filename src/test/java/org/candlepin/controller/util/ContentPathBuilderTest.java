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

package org.candlepin.controller.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.model.Environment;
import org.candlepin.model.Owner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

class ContentPathBuilderTest {

    private static final String ENV_ID = "env_id_1";
    private static final String OWNER_1 = "/org1/orgprefix";
    private static final String OWNER_2 = "ftp://ftp.site.com/content/";
    private static final String ENV_1 = "/env1/envprefix";
    private static final String ENV_2 = "https://console.redhat.com/template/";
    private static final String CONTENT_1 = "/path/to/content1";
    private static final String CONTENT_2 = "https://cdn.redhat.com/content2/path";

    @ParameterizedTest
    @MethodSource("contentPathParams")
    void shouldBuildPrefixedContentPaths(String ownerPrefix, String envPrefix, String contentPath,
        String expected) {
        Owner owner = new Owner().setContentPrefix(ownerPrefix);
        Environment environment = createEnvironment(envPrefix);
        ContentPathBuilder prefix = ContentPathBuilder.from(owner, List.of(environment));

        String fullContentPath = prefix.build(ENV_ID, contentPath);

        assertEquals(expected, fullContentPath);
    }

    public static Stream<Arguments> contentPathParams() {
        return Stream.of(
            Arguments.of(OWNER_1, null, CONTENT_1, "/org1/orgprefix/path/to/content1"),
            Arguments.of(OWNER_1, ENV_1, CONTENT_1, "/org1/orgprefix/env1/envprefix/path/to/content1"),
            Arguments.of(OWNER_1, null, CONTENT_1, "/org1/orgprefix/path/to/content1"),
            Arguments.of(OWNER_2, null, CONTENT_1, "ftp://ftp.site.com/content/path/to/content1"),
            Arguments.of(OWNER_2, ENV_1, CONTENT_1,
                "ftp://ftp.site.com/content/env1/envprefix/path/to/content1"),
            Arguments.of(OWNER_2, null, CONTENT_1, "ftp://ftp.site.com/content/path/to/content1"),
            Arguments.of(null, null, CONTENT_1, "/path/to/content1"),
            Arguments.of(null, ENV_1, CONTENT_1, "/env1/envprefix/path/to/content1"),
            Arguments.of(null, null, CONTENT_1, "/path/to/content1"),
            Arguments.of("ftp://org_prefix", "qwert://env_prefix", "/path/to/content",
                "qwert://env_prefix/path/to/content")
        );
    }

    @ParameterizedTest
    @MethodSource("fullUrlContentPaths")
    void shouldSkipOwnerAndEnvPrefixIfContentPathIsFullUrl(String ownerPrefix, String envPrefix,
        String contentPath, String expected) {
        Owner owner = new Owner().setContentPrefix(ownerPrefix);
        Environment environment = createEnvironment(envPrefix);
        ContentPathBuilder prefix = ContentPathBuilder.from(owner, List.of(environment));

        String fullContentPath = prefix.build(ENV_ID, contentPath);

        assertEquals(expected, fullContentPath);
    }

    public static Stream<Arguments> fullUrlContentPaths() {
        String fullPath = "https://cdn.redhat.com/content2/path";
        return Stream.of(
            Arguments.of(OWNER_1, null, CONTENT_2, fullPath),
            Arguments.of(OWNER_1, ENV_1, CONTENT_2, fullPath),
            Arguments.of(OWNER_1, ENV_2, CONTENT_2, fullPath),
            Arguments.of(OWNER_1, null, CONTENT_2, fullPath),
            Arguments.of(OWNER_2, null, CONTENT_2, fullPath),
            Arguments.of(OWNER_2, ENV_1, CONTENT_2, fullPath),
            Arguments.of(OWNER_2, ENV_2, CONTENT_2, fullPath),
            Arguments.of(OWNER_2, null, CONTENT_2, fullPath),
            Arguments.of(null, null, CONTENT_2, fullPath),
            Arguments.of(null, ENV_1, CONTENT_2, fullPath),
            Arguments.of(null, ENV_2, CONTENT_2, fullPath),
            Arguments.of(null, null, CONTENT_2, fullPath)
        );
    }

    @ParameterizedTest
    @MethodSource("fullUrlEnvPrefixes")
    void shouldSkipOwnerPrefixIfEnvIsFullUrl(String ownerPrefix, String envPrefix, String contentPath,
        String expected) {
        Owner owner = new Owner().setContentPrefix(ownerPrefix);
        Environment environment = createEnvironment(envPrefix);
        ContentPathBuilder prefix = ContentPathBuilder.from(owner, List.of(environment));

        String fullContentPath = prefix.build(ENV_ID, contentPath);

        assertEquals(expected, fullContentPath);
    }

    public static Stream<Arguments> fullUrlEnvPrefixes() {
        return Stream.of(
            Arguments.of(OWNER_1, ENV_2, CONTENT_1, "https://console.redhat.com/template/path/to/content1"),
            Arguments.of(OWNER_2, ENV_2, CONTENT_1, "https://console.redhat.com/template/path/to/content1"),
            Arguments.of(null, ENV_2, CONTENT_1, "https://console.redhat.com/template/path/to/content1")
        );
    }

    @ParameterizedTest
    @MethodSource("trailingSlashes")
    public void shouldHandleTrailingSlash(String ownerPrefix, String envPrefix, String contentPath,
        String expected) {
        Owner owner = new Owner().setContentPrefix(ownerPrefix);
        Environment environment = createEnvironment(envPrefix);
        ContentPathBuilder prefix = ContentPathBuilder.from(owner, List.of(environment));

        String fullContentPath = prefix.build(ENV_ID, contentPath);

        assertEquals(expected, fullContentPath);
    }

    public static Stream<Arguments> trailingSlashes() {
        String result = "/owner/env/content";
        return Stream.of(
            Arguments.of("/owner//", "env", "content", result),
            Arguments.of("/owner", "env//", "content", result),
            Arguments.of("/owner", "env", "content//", result),
            Arguments.of("/owner", "env", "content", result)
        );
    }

    @ParameterizedTest
    @MethodSource("leadingSlashes")
    public void shouldHandleLeadingSlash(String ownerPrefix, String envPrefix, String contentPath,
        String expected) {
        Owner owner = new Owner().setContentPrefix(ownerPrefix);
        Environment environment = createEnvironment(envPrefix);
        ContentPathBuilder prefix = ContentPathBuilder.from(owner, List.of(environment));

        String fullContentPath = prefix.build(ENV_ID, contentPath);

        assertEquals(expected, fullContentPath);
    }

    public static Stream<Arguments> leadingSlashes() {
        String result = "/owner/env/content";
        return Stream.of(
            Arguments.of("//owner", "env", "content", result),
            Arguments.of("/owner", "//env", "content", result),
            Arguments.of("/owner", "env", "//content", result),
            Arguments.of("owner", "env", "content", result)
        );
    }

    @Test
    public void shouldHandleNulls() {
        Owner owner = new Owner().setContentPrefix(OWNER_1);
        Environment environment = createEnvironment(ENV_1);
        ContentPathBuilder prefix = ContentPathBuilder.from(owner, List.of(environment));

        assertThrows(IllegalArgumentException.class, () -> prefix.build(ENV_ID, null));
    }

    @Test
    public void shouldHandleEnvNamePlaceholder() {
        Owner owner = new Owner().setContentPrefix(OWNER_1);
        Environment environment = createEnvironment("/env/$env");
        ContentPathBuilder prefix = ContentPathBuilder.from(owner, List.of(environment));

        String fullContentPath = prefix.build(ENV_ID, "/$env/content");

        assertEquals("/org1/orgprefix/env/env_name/env_name/content", fullContentPath);
    }

    @ParameterizedTest
    @MethodSource("urlEncoding")
    public void shouldHandleUrlEncoding(String ownerPrefix, String envPrefix, String contentPath,
        String expected) {
        Owner owner = new Owner().setContentPrefix(ownerPrefix);
        Environment environment = createEnvironment(envPrefix);
        ContentPathBuilder prefix = ContentPathBuilder.from(owner, List.of(environment));

        String fullContentPath = prefix.build(ENV_ID, contentPath);

        assertEquals(expected, fullContentPath);
    }

    public static Stream<Arguments> urlEncoding() {
        return Stream.of(
            Arguments.of("//o#wner", "env", "content", "/o%23wner/env/content"),
            Arguments.of("/owner", "//e%nv", "content", "/owner/e%25nv/content"),
            Arguments.of("/owner", "env", "//con^tent", "/owner/env/con%5Etent"),
            Arguments.of("/owner", "$releasever-123/$basearch-12", "content",
                "/owner/$releasever-123/$basearch-12/content"),
            Arguments.of("https://redhat.com/owner", "env", "content",
                "https://redhat.com/owner/env/content"),
            Arguments.of("https://redhat.com:8443///owner", "env", "content",
                "https://redhat.com:8443/owner/env/content"),
            Arguments.of("", "", "https://redhat.com:8443", "https://redhat.com:8443")
        );
    }

    private static Environment createEnvironment(String contentPrefix) {
        return new Environment().setId(ENV_ID)
            .setName("env_name")
            .setContentPrefix(contentPrefix);
    }

}
