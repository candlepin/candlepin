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
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;



@SpecTest
@OnlyInStandalone
public class ImportCleanupSpecTest {

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

        importJob = adminClient.jobs().waitForJob(importJob);
        assertThatJob(importJob)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        return importJob;
    }

    @Test
    public void shouldRemoveStackDerivedPoolWhenParentPoolIsAbsentFromManifest() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        // Create and import a manifest that has a VDC (virtual datacenter) subscription
        ProductDTO productDc = Products.random()
            .addAttributesItem(ProductAttributes.Arch.withValue("x86_64"))
            .addAttributesItem(ProductAttributes.StackingId.withValue("stack-dc"));

        ProductDTO productVdc = Products.random()
            .derivedProduct(productDc)
            .addAttributesItem(ProductAttributes.Arch.withValue("x86_64"))
            .addAttributesItem(ProductAttributes.StackingId.withValue("stack-vdc"))
            .addAttributesItem(ProductAttributes.VirtualLimit.withValue("unlimited"));

        ExportGenerator exportGenerator = new ExportGenerator()
            .addProduct(productVdc);

        this.importAsync(owner, exportGenerator.export());

        List<PoolDTO> basePools = adminClient.pools().listPoolsByProduct(owner.getId(), productVdc.getId());
        assertThat(basePools)
            .hasSize(1);

        List<PoolDTO> guestPools = adminClient.pools().listPoolsByProduct(owner.getId(), productDc.getId());
        assertThat(guestPools)
            .hasSize(1);

        // Consume the VDC pool
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));

        List<EntitlementDTO> entitlements = adminClient.consumers()
            .bindPoolSync(consumer.getUuid(), basePools.get(0).getId(), 1);
        assertThat(entitlements)
            .hasSize(1);

        // Verify a STACK_DERIVED pool is created
        List<PoolDTO> dcpools = adminClient.pools().listPoolsByProduct(owner.getId(), productDc.getId());
        assertThat(dcpools)
            .hasSize(2)
            .map(PoolDTO::getType)
            .containsExactlyInAnyOrder("UNMAPPED_GUEST", "STACK_DERIVED");

        // Import a new manifest that does not have a subscription for the VDC product, which should
        // trigger the removal of not only the VDC pool, but its derived pools as well.
        ProductDTO simpleProduct = Products.random();

        exportGenerator.clear()
            .addProduct(simpleProduct);

        this.importAsync(owner, exportGenerator.export());

        List<PoolDTO> pools = adminClient.pools().listPoolsByOwner(owner.getId());
        assertThat(pools)
            .singleElement()
            .returns(simpleProduct.getId(), PoolDTO::getProductId);
    }

}
