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

package org.candlepin.spec.jobs;

import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.PoolDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.resource.OwnerApi;
import org.candlepin.resource.PoolsApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.JobsClient;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@SpecTest
public class JobScheduleSpecTest {

    private static ApiClient client;
    private static JobsClient jobsClient;

    @BeforeAll
    public void beforeAll() throws ApiException {
        client = ApiClients.admin();
        jobsClient = client.jobs();
    }

    @Test
    @DisplayName("should not schedule arbitrary tasks")
    public void shouldNotScheduleArbitraryTasks() throws Exception {
        assertForbidden(() -> jobsClient.scheduleJob("totally-made-up"));
    }

    @Test
    @DisplayName("should not schedule non cron tasks")
    public void shouldNotScheduleNonCronTasks() throws Exception {
        assertForbidden(() -> jobsClient.scheduleJob("UndoImportsJob"));
    }

    @Test
    @DisplayName("should schedule cron tasks irrespective of the case")
    public void shouldScheduleCronTasksCaseIndependant() throws Exception {
        AsyncJobStatusDTO jobStatus = client.jobs().scheduleJob("ExpiredPoolsCleanupJob");
        assertEquals("CREATED", jobStatus.getState());
        jobsClient.waitForJobToComplete(jobStatus.getId(), 15000);
    }

    @Test
    @DisplayName("should purge expired pools")
    public void shouldPurgeExpiredPools() throws Exception {
        OwnerApi ownerApi = client.owners();
        PoolsApi poolsApi = client.pools();
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product = Products.random();
        product = client.ownerProducts().createProductByOwner(owner.getKey(), product);

        PoolDTO pool = Pools.random()
            .quantity(6L)
            .startDate(Instant.now().atOffset(ZoneOffset.UTC).minus(20, ChronoUnit.DAYS))
            .endDate(Instant.now().atOffset(ZoneOffset.UTC).minus(10, ChronoUnit.DAYS))
            .productId(product.getId());
        pool = ownerApi.createPool(owner.getKey(), pool);

        // verify pool exists before
        assertNotNull(poolsApi.getPool(pool.getId(), null, null));

        AsyncJobStatusDTO jobStatus = client.jobs().scheduleJob("ExpiredPoolsCleanupJob");
        jobsClient.waitForJobToComplete(jobStatus.getId(), 15000);

        final String poolId = pool.getId();
        assertNotFound(() -> poolsApi.getPool(poolId, null, null));
    }

    @Test
    @DisplayName("should purge import records")
    public void shouldPurgeImportRecords() throws Exception {
        // TODO change to annotation once test that introduces it is merged
        if (!client.status().status().getStandalone()) {
            return;
        }
        // TODO convert this test after import/export is written
//        @cp_export = StandardExporter.new
//        @cp_export.create_candlepin_export()
//        @cp_export_file = @cp_export.export_filename
//
//        @import_owner =@cp.create_owner(random_string("test_owner"))
//        (1. .11).each do
//          import_record = @cp.import( @import_owner['key'],
//            @cp_export_file,
//            {:force => ["SIGNATURE_CONFLICT", 'MANIFEST_SAME']})
//          import_record.status.should == 'SUCCESS'
//        end
//        records = @cp.list_imports(@import_owner['key'])
//        records.size.should == 11
//        job = @cp.trigger_job('ImportRecordCleanerJob')
//        wait_for_job(job['id'], 15)
//        records = @cp.list_imports(@import_owner['key'])
//        records.size.should == 10
//        end
    }
}
