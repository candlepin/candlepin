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
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ImportRecordDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.UpstreamConsumerDTO;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;



@SpecTest
@OnlyInStandalone
public class ImportUndoSpecTest {

    private static ApiClient adminClient;

    @BeforeAll
    public static void beforeAll() {
        assumeTrue(CandlepinMode::hasManifestGenTestExtension);

        adminClient = ApiClients.admin();
    }

    private AsyncJobStatusDTO importAsync(OwnerDTO owner, File manifest, String... force) {
        List<String> forced = force != null ? Arrays.asList(force) : List.of();

        AsyncJobStatusDTO importJob = adminClient.owners()
            .importManifestAsync(owner.getKey(), forced, manifest);

        return adminClient.jobs().waitForJob(importJob);
    }

    private AsyncJobStatusDTO undoImport(OwnerDTO owner) {
        AsyncJobStatusDTO job = adminClient.owners().undoImports(owner.getKey());
        return adminClient.jobs().waitForJob(job);
    }

    @Test
    public void shouldNotRemoveCustomPools() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        // Create a custom pool in the org
        ProductDTO product = adminClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.random());
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        // Create a basic manifest & import
        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        AsyncJobStatusDTO importJob1 = this.importAsync(owner, manifest);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        // We should have two pools after importing
        List<PoolDTO> pools1 = adminClient.pools().listPoolsByOwner(owner.getId());
        assertThat(pools1)
            .isNotNull()
            .hasSize(2);

        // Undo the import
        AsyncJobStatusDTO undoImportJob1 = this.undoImport(owner);
        assertThatJob(undoImportJob1)
            .isFinished()
            .contains("Imported pools removed");

        // Verify that our custom pool was not removed
        List<PoolDTO> pools2 = adminClient.pools().listPoolsByOwner(owner.getId());
        assertThat(pools2)
            .isNotNull()
            .singleElement()
            .extracting(PoolDTO::getId)
            .isEqualTo(pool.getId());
    }

    @Test
    public void shouldUnlinkUpstreamConsumer() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        // Create a basic manifest & import
        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        AsyncJobStatusDTO importJob1 = this.importAsync(owner, manifest);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        // The upstream consumer will be set on the owner after a successful import
        OwnerDTO updatedOwner1 = adminClient.owners().getOwner(owner.getKey());
        assertThat(updatedOwner1)
            .isNotNull()
            .extracting(OwnerDTO::getUpstreamConsumer)
            .isNotNull();

        AsyncJobStatusDTO undoImportJob1 = this.undoImport(owner);
        assertThatJob(undoImportJob1)
            .isFinished()
            .contains("Imported pools removed");

        // After undoing an import, the owner's upstream consumer should be reverted
        OwnerDTO updatedOwner2 = adminClient.owners().getOwner(owner.getKey());
        assertThat(updatedOwner2)
            .isNotNull()
            .extracting(OwnerDTO::getUpstreamConsumer)
            .isNull();
    }

    @Test
    public void shouldCreateADeleteRecordOnADeletedImport() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        AsyncJobStatusDTO importJob1 = this.importAsync(owner, manifest);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        // Before undoing, we should not have an import deletion record
        List<ImportRecordDTO> importRecords1 = adminClient.owners().getImports(owner.getKey());
        assertThat(importRecords1)
            .map(ImportRecordDTO::getStatus)
            .filteredOn("DELETE"::equals)
            .isEmpty();

        AsyncJobStatusDTO undoImportJob1 = this.undoImport(owner);
        assertThatJob(undoImportJob1)
            .isFinished()
            .contains("Imported pools removed");

        // After undoing an import, the import records should contain a deletion record
        List<ImportRecordDTO> importRecords2 = adminClient.owners().getImports(owner.getKey());
        assertThat(importRecords2)
            .map(ImportRecordDTO::getStatus)
            .filteredOn("DELETE"::equals)
            .isNotEmpty();
    }

    @Test
    public void shouldBeAbleToReimportWithoutError() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        AsyncJobStatusDTO importJob1 = this.importAsync(owner, manifest);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        OwnerDTO updatedOwner1 = adminClient.owners().getOwner(owner.getKey());
        assertThat(updatedOwner1)
            .isNotNull()
            .extracting(OwnerDTO::getUpstreamConsumer)
            .isNotNull()
            .extracting(UpstreamConsumerDTO::getUuid)
            .isNotNull();

        String upstreamConsumerUuid = updatedOwner1.getUpstreamConsumer()
            .getUuid();

        AsyncJobStatusDTO undoImportJob1 = this.undoImport(owner);
        assertThatJob(undoImportJob1)
            .isFinished()
            .contains("Imported pools removed");

        OwnerDTO updatedOwner2 = adminClient.owners().getOwner(owner.getKey());
        assertThat(updatedOwner2)
            .isNotNull()
            .extracting(OwnerDTO::getUpstreamConsumer)
            .isNull();

        // After undoing the imports, we should be able to reimport it into the same org without
        // forcing any restrictions, and without any warnings or errors
        AsyncJobStatusDTO importJob2 = this.importAsync(owner, manifest);
        assertThatJob(importJob2)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        // The upstream consumer UUID should match what it was the first time we imported this
        // manifest
        OwnerDTO updatedOwner3 = adminClient.owners().getOwner(owner.getKey());
        assertThat(updatedOwner3)
            .isNotNull()
            .extracting(OwnerDTO::getUpstreamConsumer)
            .isNotNull()
            .extracting(UpstreamConsumerDTO::getUuid)
            .isEqualTo(upstreamConsumerUuid);
    }

    @Test
    public void shouldAllowAnotherOrgToImportTheSameManifest() throws Exception {
        OwnerDTO owner1 = adminClient.owners().createOwner(Owners.random());
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());

        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        AsyncJobStatusDTO importJob1 = this.importAsync(owner1, manifest);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        // The second org should be rejected, as the manifest is already in use by another org on
        // this Candlepin instance
        AsyncJobStatusDTO importJob2 = this.importAsync(owner2, manifest);
        assertThatJob(importJob2)
            .isFailed()
            .contains("This subscription management application has already been imported by another owner");

        AsyncJobStatusDTO undoImportJob1 = this.undoImport(owner1);
        assertThatJob(undoImportJob1)
            .isFinished()
            .contains("Imported pools removed");

        // The second attempt at importing the manifest into org2 should succeed after
        // undoing/removing it from org1
        AsyncJobStatusDTO importJob3 = this.importAsync(owner2, manifest);
        assertThatJob(importJob3)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");
    }

}
