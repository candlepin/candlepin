/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ProductContentDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Products;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;



/**
 * Reproduces the concurrent manifest import race described in CANDLEPIN-1272 / SAT-48307: two or
 * more organizations importing manifests that reference the same new shared product/content data can race
 * to create that data during their respective pool refreshes, causing a database deadlock or constraint
 * violation.
 * <p></p>
 * Before the fix, one side of that race was marked as a terminal, non-retryable import failure,
 * permanently stranding that organization with unrefreshed pools. This test asserts that every
 * concurrent import eventually finishes successfully, regardless of whether it lost the race.
 */
@SpecTest
@OnlyInStandalone
public class ImportConcurrentRefreshRaceSpecTest {

    private static final int CONCURRENT_IMPORTS = 6;

    private static ApiClient adminClient;

    @BeforeAll
    public static void beforeAll() {
        assumeTrue(CandlepinMode::hasManifestGenTestExtension);

        adminClient = ApiClients.admin();
    }

    @Test
    public void shouldConcurrentImportsOfSharedNewProductEventuallySucceed() throws Exception {
        ContentDTO content = Contents.random();
        ProductDTO product = Products.random()
            .addProductContentItem(new ProductContentDTO().content(content).enabled(true));

        List<OwnerDTO> owners = new ArrayList<>();
        List<File> manifests = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_IMPORTS; i++) {
            owners.add(adminClient.owners().createOwner(Owners.random()));

            manifests.add(new ExportGenerator().addProduct(product).export());
        }

        List<Callable<AsyncJobStatusDTO>> tasks = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_IMPORTS; i++) {
            OwnerDTO owner = owners.get(i);
            File manifest = manifests.get(i);

            tasks.add(() -> adminClient.owners()
                .importManifestAsync(owner.getKey(), List.of(), manifest));
        }

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_IMPORTS);
        List<Future<AsyncJobStatusDTO>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        assertTrue(executor.awaitTermination(1, TimeUnit.MINUTES));

        for (Future<AsyncJobStatusDTO> future : futures) {
            assertThatJob(future.get())
                .terminates(adminClient)
                .isFinished()
                .contains("SUCCESS");
        }
    }

}
