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

package org.candlepin.spec.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ImportRecordDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.UpstreamConsumerDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Export;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SpecTest
@OnlyInStandalone
public class ImportErrorSpecTest {

    private ApiClient admin;
    private OwnerDTO owner;
    private ApiClient userClient;

    @BeforeEach
    public void beforeAll() {
        admin = ApiClients.admin();
        owner = admin.owners().createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, owner);
        userClient = ApiClients.basic(user);
    }

    @Test
    public void shouldReturnCorrectErrorStatusMessageOnADuplicateImport() {
        Export export = createMinimalExport();
        doImport(owner.getKey(), export.file());
        assertThatJob(doImport(owner.getKey(), export.file()))
            .isFailed()
            .contains("MANIFEST_SAME");

        List<ImportRecordDTO> importRecords = userClient.owners().getImports(owner.getKey());

        assertThat(importRecords)
            .filteredOn(record -> "FAILURE".equalsIgnoreCase(record.getStatus()))
            .map(ImportRecordDTO::getStatusMessage)
            .contains("Import is the same as existing data");
    }

    @Test
    public void shouldNotAllowImportingAnOldManifest() {
        ExportGenerator exportGenerator = new ExportGenerator(admin).minimal();
        Export exportOld = exportGenerator.export();
        sleepForMs(1000);
        Export export = exportGenerator.export();
        doImport(owner.getKey(), export.file());

        assertThatJob(doImport(owner.getKey(), exportOld.file()))
            .isFailed()
            .contains("MANIFEST_OLD");
    }

    @Test
    public void shouldAllowForcingTheSameManifest() {
        Export export = createMinimalExport();
        doImport(owner.getKey(), export.file());

        assertThatJob(doImport(owner.getKey(), export.file(), "MANIFEST_SAME", "DISTRIBUTOR_CONFLICT"))
            .isFinished();
    }

    @Test
    public void shouldAllowImportingOlderManifestsIntoAnotherOwner() {
        Export otherExportOld = createMinimalExport();
        Export export = createMinimalExport();
        doImport(owner.getKey(), export.file());
        // Old Candlepin was blocking imports in multi-tenant deployments if one org imports
        // a manifest, and then another tries with a manifest that is even slightly
        // older. This tests that this restriction no longer applies.
        OwnerDTO otherOwner = admin.owners().createOwner(Owners.random());

        assertThatJob(doImport(otherOwner.getKey(), otherExportOld.file(),
            "MANIFEST_SAME", "DISTRIBUTOR_CONFLICT"))
            .isFinished();
    }

    @Test
    public void shouldReturnConflictWhenImportingManifestFromDifferentSubscriptionManagementApplication() {
        Export export = createMinimalExport();
        doImport(owner.getKey(), export.file());
        OwnerDTO updatedOwner = admin.owners().getOwner(owner.getKey());
        String expected = "Owner has already imported from another subscription management application.";
        Export otherExport = createMinimalExport();

        assertThatJob(doImport(updatedOwner.getKey(), otherExport.file()))
            .isFailed()
            .contains("DISTRIBUTOR_CONFLICT")
            .contains(expected);

        assertThat(updatedOwner.getUpstreamConsumer())
            .extracting(UpstreamConsumerDTO::getUuid)
            .isEqualTo(export.consumer().getUuid());

        // Try again and make sure we don't see MANIFEST_SAME appear: (this was a bug)
        assertThatJob(doImport(updatedOwner.getKey(), otherExport.file()))
            .isFailed()
            .doesNotContain("MANIFEST_SAME")
            .contains("DISTRIBUTOR_CONFLICT");
    }

    /**
     * Test is run sequentially to avoid conflicts with other tests during forced reimport.
     */
    @Test
    public void shouldAllowForcingManifestFromDifferentSubscriptionManagementApplication() {
        Export export = createMinimalExport();
        doImport(owner.getKey(), export.file());
        OwnerDTO otherOwner = admin.owners().createOwner(Owners.random());
        OwnerDTO ownerBefore = admin.owners().getOwner(owner.getKey());
        Set<String> poolsBefore = listPoolIds(owner);
        Export anotherExport = createMinimalExport();
        doImport(otherOwner.getKey(), anotherExport.file(), "DISTRIBUTOR_CONFLICT");

        OwnerDTO ownerAfter = admin.owners().getOwner(owner.getKey());
        Set<String> poolsAfter = listPoolIds(owner);

        assertSameUuid(ownerBefore.getUpstreamConsumer(), ownerAfter.getUpstreamConsumer());
        assertThat(poolsBefore)
            .containsAll(poolsAfter);
    }

    @Test
    public void shouldReturnBadRequestWhenImportingManifestInUseByAnotherOwner() {
        Export export = createMinimalExport();
        doImport(owner.getKey(), export.file());
        String msg = "This subscription management application has already been imported by another owner.";
        OwnerDTO otherOwner = admin.owners().createOwner(Owners.random());

        assertThatJob(doImport(otherOwner.getKey(), export.file()))
            .isFailed()
            .contains(msg);
    }

    private Export createMinimalExport() {
        return new ExportGenerator(admin).minimal().export();
    }

    private Set<String> listPoolIds(OwnerDTO owner) {
        return userClient.pools().listPoolsByOwner(owner.getId()).stream()
            .map(PoolDTO::getId)
            .collect(Collectors.toSet());
    }

    private void assertSameUuid(UpstreamConsumerDTO consumer, UpstreamConsumerDTO other) {
        assertThat(consumer).isNotNull();
        assertThat(other).isNotNull();
        assertThat(consumer.getUuid())
            .isEqualTo(other.getUuid());
    }

    private void sleepForMs(int millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private AsyncJobStatusDTO doImport(String ownerKey, File export) {
        return importAsync(ownerKey, export, List.of());
    }

    private AsyncJobStatusDTO doImport(String ownerKey, File export, String... force) {
        return importAsync(ownerKey, export, Arrays.asList(force));
    }

    private AsyncJobStatusDTO importAsync(String ownerKey, File export, List<String> force) throws ApiException {
        AsyncJobStatusDTO importJob = admin.owners().importManifestAsync(ownerKey, force, export);
        return admin.jobs().waitForJob(importJob);
    }

}
