/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

package org.candlepin.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

public class DistinguishedNameTest {

    private static final String CN_VALUE = "test_name";
    private static final String O_VALUE = "test_org";

    @Test
    void dnShouldRequireAtLeastOneAttribute() {
        assertThatThrownBy(() -> new DistinguishedName(null, (String) null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldBuildDnWithOnlyCN() {
        DistinguishedName build = new DistinguishedName(CN_VALUE);

        assertThat(build.toString())
            .isEqualTo("CN=" + CN_VALUE);
    }

    @Test
    void shouldBuildDnWithOnlyO() {
        DistinguishedName build = new DistinguishedName(null, O_VALUE);

        assertThat(build.toString())
            .isEqualTo("O=" + O_VALUE);
    }

    @Test
    void shouldBuildDnWithCnAndO() {
        DistinguishedName build = new DistinguishedName(CN_VALUE, O_VALUE);

        assertThat(build.toString())
            .isEqualTo("CN=%s, O=%s".formatted(CN_VALUE, O_VALUE));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void cnCannotBeBlank(String cn) {
        assertThatThrownBy(() -> new DistinguishedName(cn))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void oCannotBeBlank(String o) {
        assertThatThrownBy(() -> new DistinguishedName(null, o))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
