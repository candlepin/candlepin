/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
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

import org.candlepin.model.Environment;
import org.candlepin.model.Owner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

class EntitlementContentPrefixTest {

    private static final String ENV_ID = "env_id_1";
    private static final String UNKNOWN_ENV_ID = "unknown";
    private static final String ORG_PREFIX = "/someorg/";
    private static final String EMPTY_PREFIX = "";

    @Test
    void prefixIsEmptyIfOwnerPrefixMissing() {
        Owner owner = ownerWithoutPrefix();
        ContentPrefix prefixes = EntitlementContentPrefix.from(owner, List.of());

        assertEquals(EMPTY_PREFIX, prefixes.get(ENV_ID));
        assertEquals(EMPTY_PREFIX, prefixes.get(null));
    }

    @Test
    void shouldUseOrgPrefixForUnknownEnvironments() {
        Owner owner = ownerWithPrefix(ORG_PREFIX);
        ContentPrefix prefixes = EntitlementContentPrefix.from(owner, List.of());

        assertEquals(ORG_PREFIX, prefixes.get(UNKNOWN_ENV_ID));
    }

    @Test
    void shouldUseOrgPrefixForInvalidEnvIds() {
        Owner owner = ownerWithPrefix(ORG_PREFIX);
        ContentPrefix prefixes = EntitlementContentPrefix.from(owner, List.of());

        assertEquals(ORG_PREFIX, prefixes.get(""));
        assertEquals(ORG_PREFIX, prefixes.get(" "));
    }

    @Test
    void shouldUseOwnerPrefixWhenNoEnvironmentsAreAvailable() {
        Owner owner = ownerWithPrefix(ORG_PREFIX);
        ContentPrefix prefixes = EntitlementContentPrefix.from(owner, List.of());

        assertEquals(ORG_PREFIX, prefixes.get(null));
    }

    @Test
    void prefixShouldExpandEnvironmentIfAvailable() {
        Owner owner = ownerWithPrefix("/someorg/$env/");
        List<Environment> environments = List.of(createEnv(ENV_ID, "AnAwesomeEnvironment1"));
        ContentPrefix prefixes = EntitlementContentPrefix.from(owner, environments);

        assertEquals("/someorg/AnAwesomeEnvironment1/", prefixes.get(ENV_ID));
    }

    @Test
    void prefixShouldIgnoreEnvironmentIfConsumerHasNone() {
        Owner owner = ownerWithPrefix("/someorg/$env/");
        ContentPrefix prefixes = EntitlementContentPrefix.from(owner, List.of());

        assertEquals("/someorg/$env/", prefixes.get(null));
    }

    @ParameterizedTest
    @MethodSource("prefixEncodingSource")
    void prefixShouldBeEncoded(String ownerPrefix, String envName, String expectedPrefix) {
        Owner owner = ownerWithPrefix(ownerPrefix);
        List<Environment> environments = List.of(createEnv(ENV_ID, envName));
        ContentPrefix prefixes = EntitlementContentPrefix.from(owner, environments);

        assertEquals(expectedPrefix, prefixes.get(ENV_ID));
    }

    public static Stream<Arguments> prefixEncodingSource() {
        return Stream.of(
            Arguments.of("/some org/$env/", "An Awesome Environment #1",
                "/some+org/An+Awesome+Environment+%231/"),
            Arguments.of(
                "/org! #$%&'()*+,/123:;=?@[]\"-.<>\\^_`{|}~£円/$env/",
                "foo/test environment #1/bar",
                "/org%21+%23$%25%26%27%28%29*%2B%2C/123%3A%3B%3D%3F%40%5B%5D%22" +
                    "-.%3C%3E%5C%5E_%60%7B%7C%7D%7E%C2%A3%E5%86%86/foo/test+environment+%231/bar/")
        );
    }

    @ParameterizedTest
    @MethodSource("cleanPrefixes")
    public void testCleanUpPrefixNoChange(String ownerPrefix) {
        Owner owner = ownerWithPrefix(ownerPrefix);
        List<Environment> environments = List.of(createEnv(ENV_ID, "envName"));
        ContentPrefix prefixes = EntitlementContentPrefix.from(owner, environments);

        assertEquals(ownerPrefix, prefixes.get(ENV_ID));
    }

    public static Stream<Arguments> cleanPrefixes() {
        return Stream.of(
            Arguments.of("/"),
            Arguments.of("/some_prefix/"),
            Arguments.of("/some-prefix/"),
            Arguments.of("/some.prefix/"),
            Arguments.of("/Some1Prefix2/")
        );
    }

    private Environment createEnv(String id, String name) {
        Environment environment = new Environment();
        environment.setId(id);
        environment.setName(name);
        return environment;
    }

    private Owner ownerWithoutPrefix() {
        return new Owner();
    }

    private Owner ownerWithPrefix(String prefix) {
        return new Owner().setContentPrefix(prefix);
    }

}
