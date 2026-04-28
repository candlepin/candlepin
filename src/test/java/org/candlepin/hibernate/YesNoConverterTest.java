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
package org.candlepin.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class YesNoConverterTest {

    private static final String[] VALUES = {"n", "y"};

    private static Stream<Arguments> caseInsensitiveYesNoMapping() {
        return Stream.of(
            Arguments.of('y', true),
            Arguments.of('n', false),
            Arguments.of('Y', true),
            Arguments.of('N', false)
        );
    }

    @Test
    public void testToDomainValueWithNullCharacter() {
        YesNoConverter converter = new YesNoConverter();

        assertNull(converter.toDomainValue(null));
    }

    @ParameterizedTest
    @MethodSource("caseInsensitiveYesNoMapping")
    public void testToDomainValue(char character, boolean expected) {
        YesNoConverter converter = new YesNoConverter();

        assertThat(converter.toDomainValue(character))
            .isEqualTo(expected);
    }

    @Test
    public void testToDomainValueWithUnknownCharacter() {
        YesNoConverter converter = new YesNoConverter();

        assertThrows(IllegalArgumentException.class, () -> converter.toDomainValue('k'));
    }

    @Test
    public void testToRelationalValueWithNullDomainForm() {
        YesNoConverter converter = new YesNoConverter();

        assertNull(converter.toRelationalValue(null));
    }

    @ParameterizedTest
    @MethodSource("caseInsensitiveYesNoMapping")
    public void testToRelationalValue(char expected, boolean domainForm) {
        YesNoConverter converter = new YesNoConverter();

        assertThat(converter.toRelationalValue(domainForm))
            .isEqualTo(Character.toLowerCase(expected));
    }

    @Test
    public void testGetValues() {
        YesNoConverter converter = new YesNoConverter();

        assertThat(converter.getValues())
            .isEqualTo(VALUES);
    }

}

