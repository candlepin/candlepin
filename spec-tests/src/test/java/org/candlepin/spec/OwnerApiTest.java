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
package org.candlepin.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.spec.bootstrap.client.OwnerClient;
import org.candlepin.spec.bootstrap.client.SpecTestFixture;
import org.candlepin.spec.bootstrap.data.NestedOwnerDTOBuilder;
import org.candlepin.spec.bootstrap.data.OwnerDTOBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OwnerApiTest extends SpecTestFixture {
    @Test
    public void shouldCreateOwner() throws Exception {
        OwnerDTO ownerDTO = new OwnerDTOBuilder().build();
        OwnerDTO status = getClient(OwnerClient.class).createOwner(ownerDTO);

        assertThat(status.getId()).isNotNull();
        assertThat(status.getCreated()).isNotNull();
        assertThat(status.getUpdated()).isNotNull();
        assertThat(status.getContentAccessMode()).isNotNull();
        assertThat(status.getContentAccessModeList()).isNotNull();
        assertThat(status.getKey()).isEqualTo(ownerDTO.getKey());
        assertThat(status.getDisplayName()).isEqualTo(ownerDTO.getDisplayName());
    }

    @Test
    public void shouldCreateScaOwner() throws Exception {
        OwnerDTO ownerDTO = new OwnerDTOBuilder().scaEnabled().build();
        OwnerDTO status = getClient(OwnerClient.class).createOwner(ownerDTO);

        assertThat(status.getId()).isNotNull();
        assertThat(status.getCreated()).isNotNull();
        assertThat(status.getUpdated()).isNotNull();
        assertThat(status.getContentAccessMode()).isEqualTo(ownerDTO.getContentAccessMode());
        assertThat(status.getContentAccessModeList()).isEqualTo(ownerDTO.getContentAccessModeList());
        assertThat(status.getKey()).isEqualTo(ownerDTO.getKey());
        assertThat(status.getDisplayName()).isEqualTo(ownerDTO.getDisplayName());
    }

    @Test
    public void failsToCreateOwnerWithInvalidParent() {
        OwnerDTO ownerDTO = new OwnerDTOBuilder()
            .withParent(new NestedOwnerDTOBuilder().build())
            .build();

        assertThatThrownBy(() -> getClient(OwnerClient.class).createOwner(ownerDTO))
            .isInstanceOf(ApiException.class);
    }

}
