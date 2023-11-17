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
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;



@SpecTest
@OnlyInStandalone
public class DerivedProductImportSpecTest {
    private static final String EXPECTED_CONTENT_URL = "/path/to/arch/specific/content";

    private static ApiClient adminClient;
    private static OwnerClient ownerApi;

    @BeforeAll
    public static void beforeAll() {
        assumeTrue(CandlepinMode::hasManifestGenTestExtension);

        adminClient = ApiClients.admin();
        ownerApi = adminClient.owners();
    }

    private AsyncJobStatusDTO importAsync(OwnerDTO owner, File manifest, String... force) {
        List<String> forced = force != null ? Arrays.asList(force) : List.of();

        AsyncJobStatusDTO importJob = adminClient.owners()
            .importManifestAsync(owner.getKey(), forced, manifest);

        return adminClient.jobs().waitForJob(importJob);
    }

    // TODO: FIXME: What does test have to do with importing? All of the critical logic surrounds
    // the creation of bonus pools and guest/host virt mapping junk. The import operation is just
    // a means to getting the initial pool into place. Move this to somewhere more meaningful.

    @Test
    public void shouldRetrieveSubscriptonCertificateFromDerivedPoolEntitlement() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());

        ProductDTO derivedProduct = Products.random()
            .addAttributesItem(ProductAttributes.Cores.withValue("6"))
            .addAttributesItem(ProductAttributes.Sockets.withValue("8"));

        ProductDTO sdcProduct = Products.random() // sdc = stacked datacenter
            .addAttributesItem(ProductAttributes.VirtualLimit.withValue("unlimited"))
            .addAttributesItem(ProductAttributes.StackingId.withValue("mixed-stack"))
            .addAttributesItem(ProductAttributes.Sockets.withValue("2"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .derivedProduct(derivedProduct);

        ExportGenerator exportGenerator = new ExportGenerator()
            .addProduct(sdcProduct);

        AsyncJobStatusDTO importJob1 = this.importAsync(owner, exportGenerator.export());
        assertThatJob(importJob1)
            .isNotNull()
            .isFinished();

        // Verify the target pool exists
        List<PoolDTO> pools1 = ownerApi.listOwnerPoolsByProduct(owner.getKey(), sdcProduct.getId());
        assertThat(pools1)
            .singleElement()
            .extracting(PoolDTO::getProductId)
            .isEqualTo(sdcProduct.getId());

        PoolDTO sdcPool = pools1.get(0);

        // Everything from this point on has nothing to do with import or manifests! Why is it here?

        // Note: this is *not* a Candlepin UUID, this is simulating the UUID assigned to a given
        // system by its hypervisor.
        String guestSystemUuid = StringUtil.random("uuid");

        ConsumerDTO guestConsumer = Consumers.random(owner)
            .putFactsItem("virt.uuid", guestSystemUuid)
            .putFactsItem("virt.is_guest", "true");

        ConsumerDTO hypervisorConsumer = Consumers.random(owner)
            .addGuestIdsItem(new GuestIdDTO().guestId(guestSystemUuid));

        guestConsumer = adminClient.consumers().createConsumer(guestConsumer);
        hypervisorConsumer = adminClient.consumers().createConsumer(hypervisorConsumer);

        ApiClient hypervisorClient = ApiClients.ssl(hypervisorConsumer);
        ApiClient guestClient = ApiClients.ssl(guestConsumer);

        // Have the hypervisor consume our sdc pool so it generates the guest-specific bonus pool(s)
        List<EntitlementDTO> hypervisorEnts = hypervisorClient.consumers()
            .bindPoolSync(hypervisorConsumer.getUuid(), sdcPool.getId(), 1);

        assertThat(hypervisorEnts)
            .isNotNull()
            .hasSize(1);

        // Verify our bonus pool exists and fetch its ID for later binding
        List<PoolDTO> pools2 = ownerApi.listOwnerPools(owner.getKey());
        assertThat(pools2)
            .isNotNull()
            .hasSizeGreaterThan(1);

        PoolDTO guestOnlyPool = pools2.stream()
            .filter(pool -> "STACK_DERIVED".equals(pool.getType()))
            .findAny()
            .orElseThrow();

        // Consume the bonus pool with our guest and verify we can get the upstream cert
        List<EntitlementDTO> guestEnts = guestClient.consumers()
            .bindPoolSync(guestConsumer.getUuid(), guestOnlyPool.getId(), 1);

        assertThat(guestEnts)
            .isNotNull()
            .hasSize(1);

        assertThat(adminClient.entitlements().getUpstreamCert(guestEnts.get(0).getId()))
            .startsWith("-----BEGIN CERTIFICATE-----");
    }
}
