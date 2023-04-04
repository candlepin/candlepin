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
package org.candlepin.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.model.Rules;
import org.candlepin.policy.js.RuleParseException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;


public class RulesVersionMatchingTest {

    @ParameterizedTest
    @MethodSource("validVersions")
    public void shouldParseValidVersion(String version, String expectedVersion) {
        Rules rules = new Rules(version);
        assertEquals(expectedVersion, rules.getVersion());
    }

    public static Stream<Arguments> validVersions() {
        return Stream.of(
            Arguments.of("// Version: 1.0", "1.0"),
            Arguments.of("// version: 1.0", "1.0"),
            Arguments.of("# Version: 1.0", "1.0"),
            Arguments.of("# version: 1.0", "1.0"),
            Arguments.of("//Version: 1.0", "1.0"),
            Arguments.of("//version: 1.0", "1.0"),
            Arguments.of("// Version:    1.0  ", "1.0"),
            Arguments.of("// Version: 1.0.0", "1.0.0"),
            Arguments.of("//version:1.0.0", "1.0.0"),
            Arguments.of("// Version: 1.0.0\n// This is a new line", "1.0.0")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidVersions")
    public void invalidVersionShouldThrow(String version) {
        assertThrows(RuleParseException.class, () -> new Rules(version));
    }

    public static Stream<Arguments> invalidVersions() {
        return Stream.of(
            Arguments.of(""),
            Arguments.of("// Version:  "),
            Arguments.of("// Version 1.0"),
            Arguments.of("Version 1.0"),
            Arguments.of("// Version: version"),
            Arguments.of("// Version: 1.0."),
            Arguments.of("// Version: 1.0 RC"),
            Arguments.of("// Version: 1.x"),
            Arguments.of("THISisNOTaVALIDVersion: 1")
        );
    }

}
