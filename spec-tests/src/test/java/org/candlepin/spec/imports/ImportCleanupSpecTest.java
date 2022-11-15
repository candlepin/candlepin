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
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Export;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@SpecTest
@OnlyInStandalone
public class ImportCleanupSpecTest {

    private ApiClient admin;
    private OwnerDTO owner;

    @BeforeEach
    public void beforeAll() {
        admin = ApiClients.admin();
        owner = admin.owners().createOwner(Owners.random());
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void shouldRemoveStackDerivedPoolWhenParentEntIsGone() throws InterruptedException {
        ProductDTO productDc = Products.random()
            .attributes(List.of(
                ProductAttributes.Arch.withValue("x86_64"),
                ProductAttributes.StackingId.withValue("stack-dc")));
        ProductDTO productVdc = Products.random()
            .attributes(List.of(
                ProductAttributes.Arch.withValue("x86_64"),
                ProductAttributes.StackingId.withValue("stack-vdc"),
                ProductAttributes.VirtualLimit.withValue("unlimited")))
            .derivedProduct(productDc);
        ExportGenerator exportGenerator = generateWith(generator -> generator
            .withProduct(productDc)
            .withProduct(productVdc));

        Export export = exportGenerator.export();
        List<EntitlementDTO> ents = admin.entitlements()
            .listAllForConsumer(export.consumer().getUuid(), null, null, null, null, null, null);
        for (EntitlementDTO ent : ents) {
            admin.entitlements().unbind(ent.getId());
        }
        // We need to wait here for unbind to finish
        Thread.sleep(1000L);
        Export updatedExport = exportGenerator.export();

        AsyncJobStatusDTO importJob = doImport(owner.getKey(), export.file());
        assertThat(importJob)
            .isNotNull()
            .hasFieldOrPropertyWithValue("state", "FINISHED")
            .extracting(AsyncJobStatusDTO::getResultData)
            .isNotNull()
            .hasFieldOrPropertyWithValue("status", "SUCCESS")
            .hasFieldOrPropertyWithValue("statusMessage", owner.getKey() + " file imported successfully.");

        // NORMAL pool
        List<PoolDTO> normalPools = admin.pools()
            .listPools(owner.getId(), null, productVdc.getId(), null, null, null, null, null, null);
        assertThat(normalPools)
            .hasSize(1);

        // UNMAPPED GUEST POOL
        List<PoolDTO> unmappedPools = admin.pools()
            .listPools(owner.getId(), null, productDc.getId(), true, null, null, null, null, null)
            .stream()
            .filter(pool -> pool.getType().equals("UNMAPPED_GUEST")).collect(Collectors.toList());
        assertThat(unmappedPools)
            .hasSize(1);

        // Consume NORMAL Pool
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(owner));
        JsonNode jsonNode = admin.consumers().bindPool(consumer.getUuid(), normalPools.get(0).getId(), 1);
        assertThat(jsonNode)
            .hasSize(1);

        // STACK DERIVED POOL is created
        List<PoolDTO> stack = admin.pools()
            .listPools(owner.getId(), null, productDc.getId(), null, null, null, null, null, null)
            .stream()
            .filter(pool -> pool.getType().equals("STACK_DERIVED")).collect(Collectors.toList());
        assertThat(stack)
            .hasSize(1);

        importJob = doImport(owner.getKey(), updatedExport.file());
        assertThatJob(importJob)
            .isFinished();
        normalPools = admin.pools().listPools(owner.getId(), null, null, null, null, null, null, null, null);
        assertThat(normalPools)
            .hasSize(0);
    }

    public ExportGenerator generateWith(Consumer<ExportGenerator> setup) {
        ExportGenerator exportGenerator = new ExportGenerator(admin);
        setup.accept(exportGenerator.minimal());
        return exportGenerator;
    }

    private AsyncJobStatusDTO doImport(String ownerKey, File export) {
        return importAsync(ownerKey, export, List.of());
    }

    private AsyncJobStatusDTO importAsync(String ownerKey, File export, List<String> force) throws ApiException {
        AsyncJobStatusDTO importJob = admin.owners().importManifestAsync(ownerKey, force, export);
        return admin.jobs().waitForJob(importJob);
    }
}
