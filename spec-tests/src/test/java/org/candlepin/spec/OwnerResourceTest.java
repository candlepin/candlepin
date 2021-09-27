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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.NestedOwnerDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.StatusDTO;
import org.candlepin.resource.OwnerApi;
import org.candlepin.resource.StatusApi;
import org.candlepin.spec.bootstrap.Application;
import org.candlepin.spec.bootstrap.client.ApiClientFactory;
import org.candlepin.spec.bootstrap.client.ApiClientProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpecTest
public class OwnerResourceTest {

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
    public void shouldCreateOwner() throws Exception {
        OwnerApi api = new OwnerApi(apiClient.createInstance());

        OwnerDTO ownerDTO = new OwnerDTO();
        ownerDTO.setKey("my_owner");
        ownerDTO.displayName("An Awesome Owner");

        OwnerDTO status = api.createOwner(ownerDTO);

        assertThat(status.getId()).isNotNull();
        assertThat(status.getCreated()).isNotNull();
        assertThat(status.getUpdated()).isNotNull();
        assertThat(status.getContentAccessMode()).isNotNull();
        assertThat(status.getContentAccessModeList()).isNotNull();
        assertThat(status.getKey()).isEqualTo(ownerDTO.getKey());
        assertThat(status.getDisplayName()).isEqualTo(ownerDTO.getDisplayName());

        api.deleteOwner(status.getKey(), true, true);
    }

    @Test
    public void failsToCreateOwnerWithInvalidParent() {
        OwnerApi api = new OwnerApi(apiClient.createInstance());

        NestedOwnerDTO parentOwner = new NestedOwnerDTO();
        parentOwner.displayName("An Invalid Parent");
        parentOwner.key("unknown");
        parentOwner.id("unknown");
        OwnerDTO ownerDTO = new OwnerDTO();
        ownerDTO.setKey("my_owner");
        ownerDTO.displayName("An Awesome Owner");
        ownerDTO.setParentOwner(parentOwner);

        assertThatThrownBy(() -> api.createOwner(ownerDTO))
            .isInstanceOf(ApiException.class)
            .hasMessage("Not Found");
    }

}
