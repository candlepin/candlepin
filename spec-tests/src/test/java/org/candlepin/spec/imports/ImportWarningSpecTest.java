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

import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;



@SpecTest
@OnlyInStandalone
public class ImportWarningSpecTest {

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
    public void shouldWarnAboutInactiveSubscriptions() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        OffsetDateTime now = OffsetDateTime.now();

        OffsetDateTime startDate = now.minusDays(30);
        OffsetDateTime validEndDate = now.plusDays(30);

        // This needs to be far enough for our slow CI infrastructure to be able to successfully
        // bind it, but near enough to not drive us insane during manual testing.
        OffsetDateTime expiredEndDate = now.plusSeconds(10);

        SubscriptionDTO validSubscription = Subscriptions.random()
            .product(Products.random())
            .startDate(startDate)
            .endDate(validEndDate);

        SubscriptionDTO expiredSubscription = Subscriptions.random()
            .product(Products.random())
            .startDate(startDate)
            .endDate(expiredEndDate);

        File manifest = new ExportGenerator()
            .addSubscriptions(validSubscription, expiredSubscription)
            .export();

        // Sleep until just after the target subscription actually expires
        long duration = 1000 + (expiredEndDate.toInstant().toEpochMilli() - Instant.now().toEpochMilli());
        Thread.sleep(duration);

        AsyncJobStatusDTO importJob = this.importAsync(owner, manifest);
        assertThatJob(importJob)
            .isFinished()
            .contains("SUCCESS_WITH_WARNING")
            .contains("One or more inactive subscriptions found in the file");
    }

    @Test
    public void shouldWarnAboutNoActiveSubscriptions() throws Exception {
        // Empty manifest with no subscriptions
        File manifest = new ExportGenerator().export();

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        AsyncJobStatusDTO importJob = this.importAsync(owner, manifest);
        assertThatJob(importJob)
            .isFinished()
            .contains("SUCCESS_WITH_WARNING")
            .contains("No active subscriptions found in the file");
    }

}
