/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.spec.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ImportRecordDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Export;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

@SpecTest
@OnlyInStandalone
public class ImportUndoSpecTest {

    private ApiClient admin;
    private OwnerDTO owner;
    private ApiClient userClient;
    private PoolDTO customPool;

    @BeforeEach
    public void beforeAll() {
        admin = ApiClients.admin();
        owner = admin.owners().createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, owner);
        userClient = ApiClients.basic(user);
        ProductDTO product = admin.ownerProducts().createProductByOwner(owner.getKey(), Products.random());
        // Custom pool to verify that imports are not affecting unrelated pools
        customPool = admin.owners().createPool(owner.getKey(), Pools.random(product));
    }

    @Test
    public void shouldUnlinkUpstreamConsumer() {
        Export export = createMinimalExport();
        doImport(owner.getKey(), export.file());
        undoImport(owner);

        OwnerDTO updatedOwner = admin.owners().getOwner(owner.getKey());

        assertThat(updatedOwner.getUpstreamConsumer()).isNull();
        assertOnlyCustomPoolPresent(customPool);
    }

    @Test
    public void shouldCreateADeleteRecordOnADeletedImport() {
        Export export = createMinimalExport();
        doImport(owner.getKey(), export.file());
        undoImport(owner);

        List<ImportRecordDTO> pools = userClient.owners().getImports(owner.getKey());

        assertThat(pools)
            .map(ImportRecordDTO::getStatus)
            .filteredOn("DELETE"::equals)
            .isNotEmpty();
        assertOnlyCustomPoolPresent(customPool);
    }

    @Test
    public void shouldBeAbleToReimportWithoutError() {
        Export export = createMinimalExport();
        doImport(owner.getKey(), export.file());
        undoImport(owner);

        doImport(owner.getKey(), export.file());
        OwnerDTO updatedOwner = admin.owners().getOwner(owner.getKey());

        assertThat(updatedOwner.getUpstreamConsumer())
            .hasFieldOrPropertyWithValue("uuid", export.consumer().getUuid());
        undoImport(owner);
        assertOnlyCustomPoolPresent(customPool);
    }

    @Test
    public void shouldAllowAnotherOrgToImportTheSameManifest() {
        Export export = createMinimalExport();
        doImport(owner.getKey(), export.file());
        undoImport(owner);
        OwnerDTO otherOrg = admin.owners().createOwner(Owners.random());

        assertThatNoException().isThrownBy(() -> doImport(otherOrg.getKey(), export.file()));

        admin.owners().deleteOwner(otherOrg.getKey(), true, true);
        assertOnlyCustomPoolPresent(customPool);
    }

    private void assertOnlyCustomPoolPresent(PoolDTO customPool) {
        List<PoolDTO> pools = userClient.pools().listPoolsByOwner(owner.getId());
        assertThat(pools)
            .hasSize(1)
            .map(PoolDTO::getId)
            .containsExactly(customPool.getId());
    }

    private Export createMinimalExport() {
        return new ExportGenerator(admin).minimal().export();
    }

    private void undoImport(OwnerDTO owner) {
        AsyncJobStatusDTO job = admin.owners().undoImports(owner.getKey());
        admin.jobs().waitForJob(job);
    }

    private void doImport(String ownerKey, File export) {
        importAsync(ownerKey, export, List.of());
    }

    private void importAsync(String ownerKey, File export, List<String> force) throws ApiException {
        AsyncJobStatusDTO importJob = admin.owners().importManifestAsync(ownerKey, force, export);
        admin.jobs().waitForJob(importJob);
    }

}
