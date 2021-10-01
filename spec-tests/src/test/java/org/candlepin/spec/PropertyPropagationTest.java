/*
 *  Copyright (c) 2009 - ${YEAR} Red Hat, Inc.
 *
 *  This software is licensed to you under the GNU General Public License,
 *  version 2 (GPLv2). There is NO WARRANTY for this software, express or
 *  implied, including the implied warranties of MERCHANTABILITY or FITNESS
 *  FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 *  along with this software; if not, see
 *  http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 *  Red Hat trademarks are not licensed under GPLv2. No permission is
 *  granted to use or replicate Red Hat trademarks that are incorporated
 *  in this software or its documentation.
 */
package org.candlepin.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.spec.bootstrap.client.PropertySource;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


// TODO Test to demonstrate passing properties via gradle. Run spec task with -D params for the test to pass
@Disabled
public class PropertyPropagationTest {

    private final PropertySource properties = new PropertySource();

    @Test
    public void retrievesServerStatus() {
        assertThat(this.properties.containsKey("spec.test.client.url")).isTrue();
        assertThat(this.properties.containsKey("spec.test.client.username")).isTrue();
    }

}
