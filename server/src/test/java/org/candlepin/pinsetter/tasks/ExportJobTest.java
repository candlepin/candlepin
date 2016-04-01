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

import java.util.Locale;

import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.controller.ManifestManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
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
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import com.google.inject.Provider;

@RunWith(MockitoJUnitRunner.class)
public class ExportJobTest {

    @Mock private ManifestManager manifestManager;
    @Mock private ConsumerCurator consumerCurator;
    @Mock private Provider<I18n> i18nProvider;
    @Mock private JobExecutionContext ctx;
    private ExportJob job;

    @Before
    public void setupTest() {
        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        when(i18nProvider.get()).thenReturn(i18n);
        job = new ExportJob(manifestManager, consumerCurator, i18nProvider);
    }

    @Test
    public void checkJobDetail() throws Exception {
        Consumer distributor = TestUtil.createDistributor();
        String cdnLabel = "cdn-label";
        String webappPrefix = "webapp-prefix";
        String apiUrl = "url";

        JobDetail detail = job.scheduleExport(distributor, cdnLabel, webappPrefix, apiUrl);
        JobDataMap dataMap = detail.getJobDataMap();

        assertEquals(dataMap.get(JobStatus.OWNER_ID), distributor.getOwner().getKey());
        assertEquals(dataMap.get(JobStatus.TARGET_ID), distributor.getUuid());
        assertEquals(dataMap.get(JobStatus.TARGET_TYPE), JobStatus.TargetType.CONSUMER);
        assertEquals(dataMap.get(ExportJob.CDN_LABEL), cdnLabel);
        assertEquals(dataMap.get(ExportJob.WEBAPP_PREFIX), webappPrefix);
        assertEquals(dataMap.get(ExportJob.API_URL), apiUrl);
    }

    @Test
    public void ensureJobSuccess() throws Exception {
        Consumer distributor = TestUtil.createDistributor();
        String cdnLabel = "cdn-label";
        String webappPrefix = "webapp-prefix";
        String apiUrl = "url";
        String manifestId = "1234";

        ExportResult result = new ExportResult(distributor.getUuid(), manifestId);
        when(manifestManager.generateAndStoreManifest(eq(distributor), eq(cdnLabel), eq(webappPrefix),
            eq(apiUrl))).thenReturn(result);
        when(consumerCurator.verifyAndLookupConsumer(eq(distributor.getUuid()))).thenReturn(distributor);

        JobDetail detail = job.scheduleExport(distributor, cdnLabel, webappPrefix, apiUrl);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        job.execute(ctx);

        verify(manifestManager).generateAndStoreManifest(eq(distributor), eq(cdnLabel), eq(webappPrefix),
            eq(apiUrl));
        verify(ctx).setResult(eq(result));
    }

    @Test(expected = ForbiddenException.class)
    public void ensureMustBeDistributorToExport() throws Exception {
        Consumer consumer = TestUtil.createConsumer();
        String cdnLabel = "cdn-label";
        String webappPrefix = "webapp-prefix";
        String apiUrl = "url";

        when(consumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);

        JobDetail detail = job.scheduleExport(consumer, cdnLabel, webappPrefix, apiUrl);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        job.execute(ctx);
    }
}
