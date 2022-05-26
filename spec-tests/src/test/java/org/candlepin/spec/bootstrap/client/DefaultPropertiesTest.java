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
package org.candlepin.spec.bootstrap.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;


class DefaultPropertiesTest {

    private final DefaultProperties properties = new DefaultProperties();

    @Test
    void canReadDefault() {
        assertThat(this.properties.get())
            .containsKey("spec.test.client.host")
            .containsKey("spec.test.client.port")
            .containsKey("spec.test.client.prefix")
            .containsKey("spec.test.client.username")
            .containsKey("spec.test.client.password")
            .containsKey("spec.test.client.debug")
            .doesNotContainKey("spec.test.client.unknown");
    }

}
