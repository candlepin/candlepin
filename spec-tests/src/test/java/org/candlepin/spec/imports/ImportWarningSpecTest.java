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

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.data.builder.Export;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.ExportUtil;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;



@OnlyInStandalone
public class ImportWarningSpecTest {

    private static final String ENTITLEMENTS_PATH = "export/entitlements/";

    @Test
    public void shouldWarnAboutInactiveSubscriptions() throws Exception, IOException {
        ApiClient adminClient = ApiClients.admin();
        // We need two subscriptions for this test cast. One active and one inactive.
        Export export = new ExportGenerator(adminClient)
            .minimal()
            .withProduct(Products.random())
            .withProduct(Products.random())
            .export();

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        AsyncJobStatusDTO importJob = adminClient.owners().importManifestAsync(owner.getKey(), List.of(),
            export.file());
        importJob = adminClient.jobs().waitForJob(importJob);
        assertThatJob(importJob).isFinished();

        File exportFile = export.file();
        List<EntitlementDTO> ents = adminClient.consumers().listEntitlements(export.consumer().getUuid());

        // Expire both the pool and the entitlement
        EntitlementDTO expiredEnt = ents.get(0);
        OffsetDateTime endDate = OffsetDateTime.now().minusDays(10L);
        expiredEnt.setEndDate(endDate);
        PoolDTO pool = expiredEnt.getPool();
        pool.setEndDate(endDate);
        expiredEnt.setPool(pool);

        File modifiedExport = File.createTempFile(export.file().getName().replace(".zip", ""), ".zip");
        modifiedExport.deleteOnExit();

        // Create a copy of the export to replace the existing entitlement with the expired one.
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(modifiedExport))) {
            try (ZipFile exportZip = new ZipFile(exportFile)) {
                exportZip.stream().forEach(entry -> {
                    if (entry.getName().equals("consumer_export.zip")) {
                        File modifiedConsumerExport = copyConsumerExportAndReplaceEnt(export, expiredEnt);

                        // Copy over the modified consumer_export.zip
                        transfer(entry, modifiedConsumerExport, zos);
                    }
                    else {
                        // Copy the signature file
                        transfer(exportZip, entry, zos);
                    }
                });
            }
        }

        // verify we get a warning
        importJob = adminClient.owners().importManifestAsync(owner.getKey(),
            List.of("SIGNATURE_CONFLICT", "MANIFEST_SAME"), modifiedExport);
        importJob = adminClient.jobs().waitForJob(importJob);

        assertThat(importJob)
            .returns("FINISHED", AsyncJobStatusDTO::getState)
            .extracting(AsyncJobStatusDTO::getResultData)
            .asString()
            .contains("SUCCESS_WITH_WARNING")
            .contains("%s file imported forcibly. One or more inactive subscriptions found in the file."
                .formatted(owner.getKey()));
    }

    @Test
    public void shouldWarnAboutNoActiveSubscriptions() {
        ApiClient adminClient = ApiClients.admin();
        Export export = new ExportGenerator(adminClient)
            .minimal()
            .export();

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        AsyncJobStatusDTO importJob = adminClient.owners().importManifestAsync(owner.getKey(), List.of(),
            export.file());
        importJob = adminClient.jobs().waitForJob(importJob);
        assertThat(importJob)
            .returns("FINISHED", AsyncJobStatusDTO::getState)
            .extracting(AsyncJobStatusDTO::getResultData)
            .asString()
            .contains("SUCCESS_WITH_WARNING")
            .contains("%s file imported successfully. No active subscriptions found in the file."
                .formatted(owner.getKey()));
    }

    private void transfer(ZipFile zipFile, ZipEntry entry, ZipOutputStream os) {
        try (InputStream istream = zipFile.getInputStream(entry)) {
            os.putNextEntry(new ZipEntry(entry.getName()));
            istream.transferTo(os);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void transfer(ZipEntry entry, File file, ZipOutputStream os) {
        try (InputStream istream = new FileInputStream(file)) {
            os.putNextEntry(new ZipEntry(entry.getName()));
            istream.transferTo(os);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a copy of the consumer_export.zip file in the provided export and replaces
     * the provided entitlement in the copy.
     *
     * @param export
     *  that contains the consumer_export.zip to copy and modify
     *
     * @param ent
     *  the entitlement to replace in the modified consumer_export.zip
     *
     * @return
     *  a modified copy of the consumer_export.zip
     */
    private File copyConsumerExportAndReplaceEnt(Export export, EntitlementDTO ent) {
        File temp = null;
        try {
            temp = File.createTempFile("consumer_export", ".zip");
            temp.deleteOnExit();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Create a copy of the consumer_export.zip with the modified entitlement
        String entToModify = ENTITLEMENTS_PATH + ent.getId() + ".json";
        try (ZipOutputStream zosTemp = new ZipOutputStream(new FileOutputStream(temp))) {
            ZipFile originalConsumerExport = ExportUtil.getExportArchive(export.file());
            originalConsumerExport.stream().forEach(archiveEntry -> {
                if (archiveEntry.getName().equals(entToModify)) {
                    // Change the entitlement to the modified entitlement
                    try (InputStream istream = originalConsumerExport.getInputStream(archiveEntry)) {
                        byte[] entBytes = ApiClient.MAPPER.writeValueAsBytes(ent);
                        zosTemp.putNextEntry(new ZipEntry(entToModify));
                        zosTemp.write(entBytes, 0, entBytes.length);
                    }
                    catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                else {
                    // Copy over entries we are not modifying
                    transfer(originalConsumerExport, archiveEntry, zosTemp);
                }
            });
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        return temp;
    }
}
