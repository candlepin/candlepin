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
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

@SpecTest
@OnlyInStandalone
public class ImportEnvironmentSpecTest {

    private ApiClient admin;
    private OwnerDTO owner;

    @BeforeEach
    public void beforeAll() {
        admin = ApiClients.admin();
        owner = admin.owners().createOwner(Owners.random());
    }

    @Test
    public void shouldNotRemoveCustomPoolsDuringImport() {
        Export export = new ExportGenerator(admin).minimal().withProduct(Products.random()).export();
        ProductDTO product1 = admin.ownerProducts().createProductByOwner(owner.getKey(), Products.random());
        ProductDTO product2 = admin.ownerProducts().createProductByOwner(owner.getKey(), Products.random());

        PoolDTO pool1 = admin.owners().createPool(owner.getKey(), Pools.random(product1));
        assertThat(pool1)
            .isNotNull();
        PoolDTO pool2 = admin.owners().createPool(owner.getKey(), Pools.random(product2));
        assertThat(pool2)
            .isNotNull();

        doImport(owner.getKey(), export.file());

        PoolDTO poolAfterImport1 = admin.pools().getPool(pool1.getId(), null, null);
        assertThat(poolAfterImport1)
            .isNotNull();
        PoolDTO poolAfterImport2 = admin.pools().getPool(pool2.getId(), null, null);
        assertThat(poolAfterImport2)
            .isNotNull();
    }

    private void doImport(String ownerKey, File export) {
        importAsync(ownerKey, export, List.of());
    }

    private void importAsync(String ownerKey, File export, List<String> force) throws ApiException {
        AsyncJobStatusDTO importJob = admin.owners().importManifestAsync(ownerKey, force, export);
        importJob = admin.jobs().waitForJob(importJob);
        assertThatJob(importJob)
            .isFinished();
        assertThat(importJob.getResultData())
            .isNotNull()
            .hasFieldOrPropertyWithValue("status", "SUCCESS")
            .hasFieldOrPropertyWithValue("statusMessage", ownerKey + " file imported successfully.");
    }
}
