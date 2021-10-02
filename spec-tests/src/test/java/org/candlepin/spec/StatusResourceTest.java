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

import org.candlepin.dto.api.v1.StatusDTO;
import org.candlepin.resource.StatusApi;
import org.candlepin.spec.bootstrap.client.ApiClientFactory;
import org.candlepin.spec.bootstrap.client.ClientBuilders;
import org.candlepin.spec.bootstrap.client.Config;

import org.junit.jupiter.api.Test;

/**
 * Test the /status resource
 */
@SpecTest
public class StatusResourceTest {

    private ApiClientFactory apiClient = ClientBuilders.instance();

    @Test
    public void retrievesServerStatus() throws Exception {
        StatusApi api = new StatusApi(apiClient.createInstance());

        StatusDTO status = api.status();

        assertThat(status.getMode()).isNotBlank();
        assertThat(status.getRelease()).isNotBlank();
        assertThat(status.getVersion()).isNotBlank();
        assertThat(status.getRulesVersion()).isNotBlank();
//        assertThat(status.getStandalone()).isFalse();
        assertThat(status.getRulesSource()).isEqualTo("default");
//        assertThat(status.getRulesSource()).isEqualTo("database");
        assertThat(status.getManagerCapabilities()).isNotEmpty();
    }

}
