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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobDataMap;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.controller.ManifestManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
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



@RunWith(MockitoJUnitRunner.class)
public class ExportJobTest extends BaseJobTest {

    @Mock
    private ManifestManager manifestManager;

    private ExportJob job;

    @Before
    public void setupTest() {
        super.init();

        job = new ExportJob(manifestManager);
        injector.injectMembers(job);
    }

    private Owner createTestOwner(String key, String logLevel) {
        Owner owner = TestUtil.createOwner();

        owner.setId(TestUtil.randomString());
        owner.setKey(key);
        owner.setLogLevel(logLevel);

        return owner;
    }

    @Test
    public void testJobConfigSetConsumer() {
        Owner owner = this.createTestOwner("owner_key", "log_level");
        Consumer consumer = TestUtil.createDistributor(owner);

        JobConfig config = ExportJob.createJobConfig()
            .setConsumer(consumer);

        Map<String, Object> args = config.getJobArguments();

        assertTrue(args.containsKey(ExportJob.CONSUMER_KEY));
        assertEquals(consumer.getUuid(), args.get(ExportJob.CONSUMER_KEY));
    }

    @Test
    public void testJobConfigSetOwner() {
        Owner owner = this.createTestOwner("owner_key", "log_level");

        JobConfig config = ExportJob.createJobConfig()
            .setOwner(owner);

        Map<String, String> metadata = config.getJobMetadata();

        assertTrue(metadata.containsKey(ExportJob.OWNER_KEY));
        assertEquals(owner.getKey(), metadata.get(ExportJob.OWNER_KEY));
        assertEquals(owner.getLogLevel(), config.getLogLevel());
    }

    @Test
    public void testJobConfigSetCdnLabel() {
        String label = "test_label";

        JobConfig config = ExportJob.createJobConfig()
            .setCdnLabel(label);

        Map<String, Object> args = config.getJobArguments();

        assertTrue(args.containsKey(ExportJob.CDN_LABEL));
        assertEquals(label, args.get(ExportJob.CDN_LABEL));
    }

    @Test
    public void testJobConfigSetWebAppPrefix() {
        String prefix = "test_prefix";

        JobConfig config = ExportJob.createJobConfig()
            .setWebAppPrefix(prefix);

        Map<String, Object> args = config.getJobArguments();

        assertTrue(args.containsKey(ExportJob.WEBAPP_PREFIX));
        assertEquals(prefix, args.get(ExportJob.WEBAPP_PREFIX));
    }

    @Test
    public void testJobConfigSetApiUrl() {
        String url = "test_url";

        JobConfig config = ExportJob.createJobConfig()
            .setApiUrl(url);

        Map<String, Object> args = config.getJobArguments();

        assertTrue(args.containsKey(ExportJob.API_URL));
        assertEquals(url, args.get(ExportJob.API_URL));
    }

    @Test
    public void testJobConfigSetExtensionData() {
        Map<String, String> data = new HashMap<>();
        data.put("key-1", "val-1");
        data.put("key-2", "val-2");
        data.put("key-3", "val-3");

        JobConfig config = ExportJob.createJobConfig()
            .setExtensionData(data);

        Map<String, Object> args = config.getJobArguments();

        assertTrue(args.containsKey(ExportJob.EXTENSION_DATA));
        assertEquals(data, args.get(ExportJob.EXTENSION_DATA));
    }

    @Test
    public void testValidate() throws JobConfigValidationException {
        Owner owner = this.createTestOwner("owner_key", "log_level");
        Consumer consumer = TestUtil.createDistributor(owner);

        JobConfig config = ExportJob.createJobConfig()
            .setConsumer(consumer)
            .setCdnLabel("test_label");

        config.validate();
    }

    // TODO: Update this test to use the JUnit5 exception handling once this branch catches up
    // with master
    @Test
    public void testValidateNoConsumer() {
        Owner owner = this.createTestOwner("owner_key", "log_level");
        Consumer consumer = TestUtil.createDistributor(owner);

        JobConfig config = ExportJob.createJobConfig()
            .setCdnLabel("test_label");

        try {
            config.validate();
            fail("an expected exception was not thrown");
        }
        catch (JobConfigValidationException e) {
            // Pass!
        }
    }

    // TODO: Update this test to use the JUnit5 exception handling once this branch catches up
    // with master
    @Test
    public void testValidateNoLabel() {
        Owner owner = this.createTestOwner("owner_key", "log_level");
        Consumer consumer = TestUtil.createDistributor(owner);

        JobConfig config = ExportJob.createJobConfig()
            .setConsumer(consumer);

        try {
            config.validate();
            fail("an expected exception was not thrown");
        }
        catch (JobConfigValidationException e) {
            // Pass!
        }
    }

    @Test
    public void ensureJobSuccess() throws Exception {
        Owner owner = this.createTestOwner("owner_key", "log_level");
        Consumer distributor = TestUtil.createDistributor(owner);

        String manifestId = "1234";
        String cdnLabel = "test_label";
        String appPrefix = "test_prefix";
        String apiUrl = "test_url";

        Map<String, String> extData = new HashMap<>();
        extData.put("key-1", "val-1");
        extData.put("key-2", "val-2");
        extData.put("key-3", "val-3");

        ExportResult result = new ExportResult(distributor.getUuid(), manifestId);

        doReturn(result).when(manifestManager).generateAndStoreManifest(
            eq(distributor.getUuid()), eq(cdnLabel), eq(appPrefix), eq(apiUrl), eq(extData));

        JobConfig config = ExportJob.createJobConfig()
            .setConsumer(distributor)
            .setOwner(owner)
            .setCdnLabel(cdnLabel)
            .setWebAppPrefix(appPrefix)
            .setApiUrl(apiUrl)
            .setExtensionData(extData);

        JobExecutionContext context = mock(JobExecutionContext.class);
        doReturn(new JobDataMap(config.getJobArguments())).when(context).getJobData();

        Object actualResult = this.job.execute(context);

        assertEquals(result, actualResult);
    }

}
