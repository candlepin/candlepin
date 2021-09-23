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

import org.candlepin.ApiClient;
import org.candlepin.dto.api.v1.StatusDTO;
import org.candlepin.resource.StatusApi;
import org.candlepin.spec.bootstrap.Application;
import org.candlepin.spec.bootstrap.client.ApiClientFactory;
import org.candlepin.spec.bootstrap.client.ApiClientProperties;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test the /status resource
 */
//@SpecTest
@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = Application.class)
@AutoConfigureMockMvc
@EnableConfigurationProperties(value = ApiClientProperties.class)
//@TestPropertySource(
//    locations = "classpath:application-integrationtest.properties")
public class StatusResourceTest {

    @Autowired
//    @Qualifier("adminApiClient")
//    private ApiClient apiClient;
    private ApiClientFactory apiClient;
//    private ApiClientFactory apiClient = new ApiClientFactory(
//        new ApiClientProperties(
//            "https://192.168.122.12:8443/candlepin",
//            "admin",
//            "admin",
//            true
//        ));


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
