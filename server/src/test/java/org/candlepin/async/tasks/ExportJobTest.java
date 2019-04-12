/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.async.tasks;

import org.candlepin.async.JobBuilder;
import org.candlepin.async.JobDataMap;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.controller.ManifestManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.pinsetter.tasks.BaseJobTest;
import org.candlepin.sync.ExportResult;
import org.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ExportJobTest extends BaseJobTest {

    private static final String CDN_LABEL = "cdn-label";
    private static final String WEBAPP_PREFIX = "webapp-prefix";
    private static final String API_URL = "url";

    @Mock
    private ManifestManager manifestManager;
    @Mock
    private JobExecutionContext ctx;
    private ExportJob job;

    @Before
    public void setupTest() {
        super.init();
        job = new ExportJob(manifestManager);
        injector.injectMembers(job);
    }

    @Test
    public void checkJobDetail() {
        final Owner owner = TestUtil.createOwner();
        owner.setId(TestUtil.randomString());
        final Consumer distributor = TestUtil.createDistributor(owner);

        final Map<String, String> extData = new HashMap<>();
        extData.put("version", "sat-6.2");

        final JobBuilder detail = ExportJob.scheduleExport(
            distributor, owner, CDN_LABEL, WEBAPP_PREFIX, API_URL, extData);
        final Map<String, Object> dataMap = detail.getJobArguments();

        assertEquals(dataMap.get(JobStatus.OWNER_ID), owner.getKey());
        assertEquals(dataMap.get(JobStatus.TARGET_ID), distributor.getUuid());
        assertEquals(dataMap.get(JobStatus.TARGET_TYPE), JobStatus.TargetType.CONSUMER);
        assertEquals(dataMap.get(ExportJob.CDN_LABEL), CDN_LABEL);
        assertEquals(dataMap.get(ExportJob.WEBAPP_PREFIX), WEBAPP_PREFIX);
        assertEquals(dataMap.get(ExportJob.API_URL), API_URL);
        assertEquals(dataMap.get(ExportJob.EXTENSION_DATA), extData);
    }

    @Test
    public void ensureJobSuccess() throws Exception {
        final Owner owner = TestUtil.createOwner();
        owner.setId(TestUtil.randomString());
        final Consumer distributor = TestUtil.createDistributor(owner);
        final String manifestId = "1234";
        final Map<String, String> extData = new HashMap<>();
        final ExportResult result = new ExportResult(distributor.getUuid(), manifestId);
        doReturn(result).when(manifestManager).generateAndStoreManifest(
            eq(distributor.getUuid()),
            eq(CDN_LABEL),
            eq(WEBAPP_PREFIX),
            eq(API_URL),
            eq(extData));
        final JobBuilder detail = ExportJob.scheduleExport(
            distributor, owner, CDN_LABEL, WEBAPP_PREFIX, API_URL, extData);
        when(ctx.getJobData()).thenReturn(new JobDataMap(detail.getJobArguments()));

        final Object actualResult = job.execute(ctx);

        assertEquals(result, actualResult);
        verify(manifestManager).generateAndStoreManifest(
            eq(distributor.getUuid()),
            eq(CDN_LABEL),
            eq(WEBAPP_PREFIX),
            eq(API_URL),
            eq(extData));

    }

}
