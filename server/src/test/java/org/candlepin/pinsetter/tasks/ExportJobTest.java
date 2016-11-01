/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.pinsetter.tasks;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.candlepin.controller.ManifestManager;
import org.candlepin.model.Consumer;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.sync.ExportResult;
import org.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;


@RunWith(MockitoJUnitRunner.class)
public class ExportJobTest {

    @Mock private ManifestManager manifestManager;
    @Mock private JobExecutionContext ctx;
    private ExportJob job;

    @Before
    public void setupTest() {
        job = new ExportJob(manifestManager);
    }

    @Test
    public void checkJobDetail() throws Exception {
        Consumer distributor = TestUtil.createDistributor();
        String cdnLabel = "cdn-label";
        String webappPrefix = "webapp-prefix";
        String apiUrl = "url";

        Map<String, String> extData = new HashMap<String, String>();
        extData.put("version", "sat-6.2");

        JobDetail detail = job.scheduleExport(distributor, cdnLabel, webappPrefix, apiUrl, extData);
        JobDataMap dataMap = detail.getJobDataMap();

        assertEquals(dataMap.get(JobStatus.OWNER_ID), distributor.getOwner().getKey());
        assertEquals(dataMap.get(JobStatus.TARGET_ID), distributor.getUuid());
        assertEquals(dataMap.get(JobStatus.TARGET_TYPE), JobStatus.TargetType.CONSUMER);
        assertEquals(dataMap.get(ExportJob.CDN_LABEL), cdnLabel);
        assertEquals(dataMap.get(ExportJob.WEBAPP_PREFIX), webappPrefix);
        assertEquals(dataMap.get(ExportJob.API_URL), apiUrl);
        assertEquals(dataMap.get(ExportJob.EXTENSION_DATA), extData);
    }

    @Test
    public void ensureJobSuccess() throws Exception {
        Consumer distributor = TestUtil.createDistributor();
        String cdnLabel = "cdn-label";
        String webappPrefix = "webapp-prefix";
        String apiUrl = "url";
        String manifestId = "1234";
        Map<String, String> extData = new HashMap<String, String>();

        ExportResult result = new ExportResult(distributor.getUuid(), manifestId);
        when(manifestManager.generateAndStoreManifest(eq(distributor.getUuid()), eq(cdnLabel),
            eq(webappPrefix), eq(apiUrl),  eq(extData))).thenReturn(result);

        JobDetail detail = job.scheduleExport(distributor, cdnLabel, webappPrefix, apiUrl, extData);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        job.execute(ctx);

        verify(manifestManager).generateAndStoreManifest(eq(distributor.getUuid()), eq(cdnLabel),
            eq(webappPrefix), eq(apiUrl), eq(extData));
        verify(ctx).setResult(eq(result));
    }

}
