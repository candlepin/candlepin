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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.NestedOwnerDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.resource.OwnerApi;
import org.candlepin.spec.bootstrap.client.ApiClientFactory;
import org.candlepin.spec.bootstrap.client.ClientBuilders;
import org.candlepin.spec.bootstrap.client.Config;
import org.candlepin.spec.bootstrap.client.OwnerClient;
import org.candlepin.spec.bootstrap.data.Owners;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SpecTest
public class OwnerResourceTest {

    private ApiClientFactory apiClient = ClientBuilders.instance();
    private OwnerClient api;

    @BeforeEach
    void setUp() {
        this.api = new OwnerClient(apiClient.createInstance());
    }

    @AfterEach
    void tearDown() throws ApiException {
        this.api.cleanup();
    }

    @Test
    public void shouldCreateOwner() throws Exception {
        OwnerApi api = new OwnerApi(apiClient.createInstance());
        OwnerDTO ownerDTO = Owners.simple();

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
