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

package org.candlepin.controller.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.model.Environment;
import org.candlepin.model.Owner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

class ScaContainerContentPrefixTest {

    private static final boolean HOSTED = false;
    private static final boolean STANDALONE = true;
    private static final String ENV_ID = "env_id_1";
    private static final String UNKNOWN_ENV_ID = "unknown_env_id_1";

    @Test
    void prefixShouldBePrefixedInHosted() {
        Owner owner = ownerWithKey("owner_key_1");
        List<Environment> environments = List.of(createEnv("AnAwesomeEnvironment1"));
        ContentPrefix prefixes = ScaContainerContentPrefix.from(owner, HOSTED, environments);

        String expected = "/sca/" + owner.getKey();

        assertEquals(expected, prefixes.get(null));
        assertEquals(expected, prefixes.get(ENV_ID));
    }

    @ParameterizedTest
    @MethodSource("prefixEncodingSource2")
    void shouldEncodePrefixInHosted(String ownerKey, String expectedPrefix) {
        Owner owner = ownerWithKey(ownerKey);
        ContentPrefix prefixes = ScaContainerContentPrefix.from(owner, HOSTED, List.of());

        assertEquals(expectedPrefix, prefixes.get(ENV_ID));
    }

    public static Stream<Arguments> prefixEncodingSource2() {
        return Stream.of(
            Arguments.of("owner key #1", "/sca/owner+key+%231"),
            Arguments.of(
                "org! #$%&'()*+,/123:;=?@[]\"-.<>\\^_`{|}~£円",
                "/sca/org%21+%23%24%25%26%27%28%29*%2B%2C%2F123%3A%3B%3D%3F%40%5B%5D%22" +
                    "-.%3C%3E%5C%5E_%60%7B%7C%7D%7E%C2%A3%E5%86%86")
        );
    }

    @Test
    void shouldUseOwnerPrefixForNullEnvironments() {
        Owner owner = ownerWithKey("owner_key_1");
        ContentPrefix prefixes = ScaContainerContentPrefix.from(owner, STANDALONE, List.of());

        assertEquals("/owner_key_1", prefixes.get(null));
    }

    @Test
    void shouldUseOwnerPrefixForUnknownEnvironments() {
        Owner owner = ownerWithKey("owner_key_1");
        List<Environment> environments = List.of(createEnv("AnAwesomeEnvironment1"));
        ContentPrefix prefixes = ScaContainerContentPrefix.from(owner, STANDALONE, environments);

        assertEquals("/owner_key_1", prefixes.get(UNKNOWN_ENV_ID));
    }

    @Test
    void prefixShouldAppendEnvIfAvailable() {
        Owner owner = ownerWithKey("owner_key_1");
        List<Environment> environments = List.of(createEnv("AnAwesomeEnvironment1"));
        ContentPrefix prefixes = ScaContainerContentPrefix.from(owner, STANDALONE, environments);

        assertEquals("/owner_key_1/AnAwesomeEnvironment1", prefixes.get(ENV_ID));
    }

    @ParameterizedTest
    @MethodSource("prefixEncodingSource")
    void prefixShouldBeEncoded(String ownerKey, String envName, String expectedPrefix) {
        Owner owner = ownerWithKey(ownerKey);
        List<Environment> environments = List.of(createEnv(envName));
        ContentPrefix prefixes = ScaContainerContentPrefix.from(owner, STANDALONE, environments);

        assertEquals(expectedPrefix, prefixes.get(ENV_ID));
    }

    public static Stream<Arguments> prefixEncodingSource() {
        return Stream.of(
            Arguments.of("owner key #1", "An/Awesome/Environment/#1",
                "/owner+key+%231/An/Awesome/Environment/%231"),
            Arguments.of(
                "org! #$%&'()*+,/123:;=?@[]\"-.<>\\^_`{|}~£円",
                "foo/test environment #1/bar",
                "/org%21+%23%24%25%26%27%28%29*%2B%2C%2F123%3A%3B%3D%3F%40%5B%5D%22" +
                    "-.%3C%3E%5C%5E_%60%7B%7C%7D%7E%C2%A3%E5%86%86/foo/test+environment+%231/bar")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"An/AwesomeEnvironment1", "An/Awesome/Environment1", "An/Awesome/Environment/1"})
    void prefixShouldNotEncodeSlashesInEnvNames(String envName) {
        Owner owner = ownerWithKey("owner_key_1");
        List<Environment> environments = List.of(createEnv(envName));
        ContentPrefix prefixes = ScaContainerContentPrefix.from(owner, STANDALONE, environments);

        assertEquals("/owner_key_1/" + envName, prefixes.get(ENV_ID));
    }

    private Environment createEnv(String name) {
        Environment environment = new Environment();
        environment.setId(ENV_ID);
        environment.setName(name);
        return environment;
    }

    private Owner ownerWithKey(String key) {
        return new Owner().setKey(key);
    }

}
