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
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;

import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.spec.bootstrap.client.OwnerClient;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.SpecTestFixture;
import org.candlepin.spec.bootstrap.data.builder.Owners;

import org.junit.jupiter.api.Test;

@SpecTest
class OwnerSpecTest extends SpecTestFixture {

    @Test
    void shouldCreateOwner() throws Exception {
        OwnerDTO ownerDTO = Owners.random();
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
    void shouldCreateScaOwner() throws Exception {
        OwnerDTO ownerDTO = Owners.randomSca();
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
    void failsToCreateOwnerWithInvalidParent() {
        OwnerDTO ownerDTO = Owners.random()
            .parentOwner(Owners.toNested(Owners.random()));

        assertNotFound(() -> getClient(OwnerClient.class).createOwner(ownerDTO));
    }

}
