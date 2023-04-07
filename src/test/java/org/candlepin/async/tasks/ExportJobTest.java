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
package org.candlepin.async.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.controller.ManifestManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.sync.ExportResult;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class ExportJobTest {

    @Mock
    private ManifestManager manifestManager;
    private ExportJob job;

    @BeforeEach
    public void setupTest() {
        job = new ExportJob(manifestManager);
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

        JobArguments args = config.getJobArguments();

        assertTrue(args.containsKey(ExportJob.CONSUMER_KEY));
        assertEquals(consumer.getUuid(), args.getAsString(ExportJob.CONSUMER_KEY));
    }

    @Test
    public void testJobConfigSetOwner() {
        Owner owner = this.createTestOwner("owner_key", "log_level");

        JobConfig config = ExportJob.createJobConfig()
            .setOwner(owner);

        assertEquals(owner, config.getContextOwner());
    }

    @Test
    public void testJobConfigSetCdnLabel() {
        String label = "test_label";

        JobConfig config = ExportJob.createJobConfig()
            .setCdnLabel(label);

        JobArguments args = config.getJobArguments();

        assertTrue(args.containsKey(ExportJob.CDN_LABEL));
        assertEquals(label, args.getAsString(ExportJob.CDN_LABEL));
    }

    @Test
    public void testJobConfigSetWebAppPrefix() {
        String prefix = "test_prefix";

        JobConfig config = ExportJob.createJobConfig()
            .setWebAppPrefix(prefix);

        JobArguments args = config.getJobArguments();

        assertTrue(args.containsKey(ExportJob.WEBAPP_PREFIX));
        assertEquals(prefix, args.getAsString(ExportJob.WEBAPP_PREFIX));
    }

    @Test
    public void testJobConfigSetApiUrl() {
        String url = "test_url";

        JobConfig config = ExportJob.createJobConfig()
            .setApiUrl(url);

        JobArguments args = config.getJobArguments();

        assertTrue(args.containsKey(ExportJob.API_URL));
        assertEquals(url, args.getAsString(ExportJob.API_URL));
    }

    @Test
    public void testJobConfigSetExtensionData() {
        Map<String, String> data = new HashMap<>();
        data.put("key-1", "val-1");
        data.put("key-2", "val-2");
        data.put("key-3", "val-3");

        JobConfig config = ExportJob.createJobConfig()
            .setExtensionData(data);

        JobArguments args = config.getJobArguments();

        assertTrue(args.containsKey(ExportJob.EXTENSION_DATA));
        assertEquals(data, args.getAs(ExportJob.EXTENSION_DATA, Map.class));
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

    @Test
    public void testValidateCDNLabelNotRequired() throws JobConfigValidationException {
        Owner owner = this.createTestOwner("owner_key", "log_level");
        Consumer consumer = TestUtil.createDistributor(owner);

        JobConfig config = ExportJob.createJobConfig()
            .setConsumer(consumer);

        config.validate();
    }

    @Test
    public void testValidateNoConsumer() {
        Owner owner = this.createTestOwner("owner_key", "log_level");
        Consumer consumer = TestUtil.createDistributor(owner);

        JobConfig config = ExportJob.createJobConfig()
            .setCdnLabel("test_label");

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @Test
    public void ensureJobSuccess() throws Exception {
        Owner owner = this.createTestOwner("owner_key", "log_level");
        Consumer distributor = TestUtil.createDistributor(owner);

        String manifestId = "1234";
        String cdnLabel = "test_label";
        String appPrefix = "test_prefix";
        String apiUrl = "test_url";

        ExportResult result = new ExportResult(distributor.getUuid(), manifestId);

        doReturn(result).when(manifestManager).generateAndStoreManifest(
            eq(distributor.getUuid()), eq(cdnLabel), eq(appPrefix), eq(apiUrl));

        JobConfig config = ExportJob.createJobConfig()
            .setConsumer(distributor)
            .setOwner(owner)
            .setCdnLabel(cdnLabel)
            .setWebAppPrefix(appPrefix)
            .setApiUrl(apiUrl);

        JobExecutionContext context = mock(JobExecutionContext.class);
        doReturn(config.getJobArguments()).when(context).getJobArguments();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        job.execute(context);

        verify(context, times(1)).setJobResult(captor.capture());
        Object actualResult = captor.getValue();

        assertEquals(result, actualResult);
    }

}
