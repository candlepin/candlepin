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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ImportRecordDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.UpstreamConsumerDTO;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Products;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;



@SpecTest
@OnlyInStandalone
public class ImportErrorSpecTest {

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

    @Test
    public void shouldReturnCorrectErrorStatusMessageOnADuplicateImport() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        // Create a simple manifest to throw at the server twice
        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        AsyncJobStatusDTO importJob1 = this.importAsync(owner, manifest);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        AsyncJobStatusDTO importJob2 = this.importAsync(owner, manifest);
        assertThatJob(importJob2)
            .isFailed()
            .contains("MANIFEST_SAME");

        List<ImportRecordDTO> importRecords = adminClient.owners().getImports(owner.getKey());

        assertThat(importRecords)
            .filteredOn(record -> "FAILURE".equalsIgnoreCase(record.getStatus()))
            .map(ImportRecordDTO::getStatusMessage)
            .contains("Import is the same as existing data");
    }

    @Test
    public void shouldAllowForcingTheSameManifest() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        // Create a simple manifest to throw at the server twice
        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        AsyncJobStatusDTO importJob1 = this.importAsync(owner, manifest);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS");

        // Attempting to import this manifest again should fail with "MANIFEST_SAME"
        AsyncJobStatusDTO importJob2 = this.importAsync(owner, manifest);
        assertThatJob(importJob2)
            .isFailed()
            .contains("MANIFEST_SAME");

        // ...but forcing it should succeed
        AsyncJobStatusDTO importJob3 = this.importAsync(owner, manifest, "MANIFEST_SAME");
        assertThatJob(importJob3)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");
    }

    @Test
    public void shouldNotAllowImportingAnOldManifest() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        ExportGenerator exportGenerator = new ExportGenerator();

        // Impl note: it's probably not strictly necessary to add or modify the subscription data
        // for this test, but it helps ensure we don't hit a "MANIFEST_SAME" error when we're not
        // looking for it.
        File oldManifest = exportGenerator.addProduct(Products.random())
            .export();

        Thread.sleep(1500);

        File newManifest = exportGenerator.addProduct(Products.random())
            .export();

        // Import the new manifest first
        AsyncJobStatusDTO importJob1 = this.importAsync(owner, newManifest);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        // Import the old manifest, which should be rejected for being older than the last manifest
        // we've imported.
        AsyncJobStatusDTO importJob2 = this.importAsync(owner, oldManifest);
        assertThatJob(importJob2)
            .isFailed()
            .contains("MANIFEST_OLD");
    }

    @Test
    public void shouldAllowImportingOlderManifestsIntoAnotherOwner() throws Exception {
        OwnerDTO owner1 = adminClient.owners().createOwner(Owners.random());
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());

        // Impl note: We cannot reuse the same generator here; the manifests must come from
        // different upstream orgs or we'll get a different, unrecoverable failure.
        File oldManifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        Thread.sleep(1500);

        File newManifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        // Import the new manifest to the first org
        AsyncJobStatusDTO importJob1 = this.importAsync(owner1, newManifest);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        // Old Candlepin was blocking imports in multi-tenant deployments if one org imports
        // a manifest, and then another tries with a manifest that is even slightly
        // older. This tests that this restriction no longer applies.
        AsyncJobStatusDTO importJob2 = this.importAsync(owner2, oldManifest);
        assertThatJob(importJob2)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");
    }

    @Test
    public void shouldReturnConflictWhenImportingManifestFromDifferentSubscriptionManagementApplication()
        throws Exception {

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        File manifest1 = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        AsyncJobStatusDTO importJob1 = this.importAsync(owner, manifest1);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        // Impl note: we want the manifest to come from different upstream sources, so we can't
        // reuse the generator
        File manifest2 = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        AsyncJobStatusDTO importJob2 = this.importAsync(owner, manifest2);
        assertThatJob(importJob2)
            .isFailed()
            .contains("DISTRIBUTOR_CONFLICT");
    }

    /**
     * Test is run sequentially to avoid conflicts with other tests during forced reimport.
     */
    @Test
    public void shouldAllowForcingManifestFromDifferentSubscriptionManagementApplication() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        File manifest1 = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        AsyncJobStatusDTO importJob1 = this.importAsync(owner, manifest1);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        OwnerDTO ownerRefresh1 = adminClient.owners().getOwner(owner.getKey());
        assertThat(ownerRefresh1)
            .isNotNull()
            .extracting(OwnerDTO::getUpstreamConsumer)
            .isNotNull()
            .extracting(UpstreamConsumerDTO::getUuid)
            .isNotNull();

        String upstreamConsumerUuid1 = ownerRefresh1.getUpstreamConsumer()
            .getUuid();

        // Impl note: we want the manifest to come from different upstream sources, so we can't
        // reuse the generator
        File manifest2 = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        // Importing manifest2 should fail since it has a different upstream source
        AsyncJobStatusDTO importJob2 = this.importAsync(owner, manifest2);
        assertThatJob(importJob2)
            .isFailed()
            .contains("DISTRIBUTOR_CONFLICT");

        // ...but forcing the distributor conflict should allow it to succeed without other issues
        AsyncJobStatusDTO importJob3 = this.importAsync(owner, manifest2, "DISTRIBUTOR_CONFLICT");
        assertThatJob(importJob3)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING")
            .contains("file imported forcibly");

        OwnerDTO ownerRefresh2 = adminClient.owners().getOwner(owner.getKey());
        assertThat(ownerRefresh2)
            .isNotNull()
            .extracting(OwnerDTO::getUpstreamConsumer)
            .isNotNull()
            .extracting(UpstreamConsumerDTO::getUuid)
            .isNotNull();

        String upstreamConsumerUuid2 = ownerRefresh2.getUpstreamConsumer()
            .getUuid();

        // The upstream consumer UUID after importing the second manifest should no longer match the
        // upstream consumer UUID of the first manifest.
        assertNotEquals(upstreamConsumerUuid1, upstreamConsumerUuid2);
    }

    @Test
    public void shouldReturnBadRequestWhenImportingManifestInUseByAnotherOwner() throws Exception {
        OwnerDTO owner1 = adminClient.owners().createOwner(Owners.random());
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());

        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        // The first org should be able to import this manifest without issue
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
    }

}
