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
package org.candlepin.async.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.async.ArgumentConversionException;
import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.controller.ManifestManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.sync.ExportCreationException;
import org.candlepin.sync.ExportResult;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
class ExportJobV2Test {
    @Mock
    private ManifestManager manifestManager;
    @Mock
    private JobExecutionContext context;
    private ExportJobV2 job;

    @BeforeEach
    void setupTest() {
        this.job = new ExportJobV2(this.manifestManager);
    }

    private Owner createTestOwner() {
        return TestUtil.createOwner()
            .setId("owner-id")
            .setKey("owner_key")
            .setLogLevel("debug");
    }

    @Test
    void testJobConfigIdentity() {
        JobConfig config = ExportJobV2.createJobConfig();

        assertEquals(ExportJobV2.JOB_KEY, config.getJobKey());
        assertEquals(ExportJobV2.JOB_NAME, config.getJobName());
    }

    @Test
    void testJobConfigSetConsumer() {
        Consumer consumer = TestUtil.createDistributor(this.createTestOwner());
        ExportJobV2.ExportJobConfigV2 config = ExportJobV2.createJobConfig();

        assertSame(config, config.setConsumer(consumer));
        JobArguments arguments = config.getJobArguments();
        assertTrue(arguments.containsKey(ExportJobV2.CONSUMER_KEY));
        assertEquals(consumer.getUuid(), arguments.getAsString(ExportJobV2.CONSUMER_KEY));
    }

    @Test
    void testJobConfigSetNullConsumer() {
        assertThrows(IllegalArgumentException.class, () -> ExportJobV2.createJobConfig().setConsumer(null));
    }

    @Test
    void testJobConfigSetOwner() {
        Owner owner = this.createTestOwner();
        JobConfig config = ExportJobV2.createJobConfig().setOwner(owner);

        assertSame(owner, config.getContextOwner());
    }

    @Test
    void testValidate() throws JobConfigValidationException {
        Consumer consumer = TestUtil.createDistributor(this.createTestOwner());
        JobConfig config = ExportJobV2.createJobConfig().setConsumer(consumer);

        config.validate();
    }

    @Test
    void testValidateNoConsumer() {
        JobConfig config = ExportJobV2.createJobConfig();

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testValidateConsumerWithoutUuid(String uuid) {
        Consumer consumer = TestUtil.createDistributor(this.createTestOwner()).setUuid(uuid);
        JobConfig config = ExportJobV2.createJobConfig().setConsumer(consumer);

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @Test
    void testValidateInvalidConsumerArgumentType() {
        JobConfig config = ExportJobV2.createJobConfig()
            .setJobArgument(ExportJobV2.CONSUMER_KEY, Map.of("uuid", "consumer-uuid"));

        JobConfigValidationException failure = assertThrows(JobConfigValidationException.class, config::validate);

        assertInstanceOf(ArgumentConversionException.class, failure.getCause());
    }

    @Test
    void ensureJobSuccess() throws Exception {
        Owner owner = this.createTestOwner();
        Consumer consumer = TestUtil.createDistributor(owner);
        ExportResult result = new ExportResult(consumer.getUuid(), "manifest-id");
        when(this.manifestManager.generateAndStoreManifestV2(consumer.getUuid())).thenReturn(result);
        JobConfig config = ExportJobV2.createJobConfig()
            .setConsumer(consumer)
            .setOwner(owner);
        when(this.context.getJobArguments()).thenReturn(config.getJobArguments());

        this.job.execute(this.context);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(this.context).setJobResult(captor.capture());
        assertSame(result, captor.getValue());
        verify(this.manifestManager).generateAndStoreManifestV2(consumer.getUuid());
        verifyNoMoreInteractions(this.manifestManager);
    }

    private static Stream<Exception> exportFailures() {
        return Stream.of(new ExportCreationException("export failed"), new IllegalStateException("storage failed"));
    }

    @ParameterizedTest
    @MethodSource("exportFailures")
    void ensureJobFailureIsRetryable(Exception cause) throws Exception {
        Consumer consumer = TestUtil.createDistributor(this.createTestOwner());
        JobConfig config = ExportJobV2.createJobConfig().setConsumer(consumer);
        when(this.context.getJobArguments()).thenReturn(config.getJobArguments());
        when(this.manifestManager.generateAndStoreManifestV2(consumer.getUuid())).thenThrow(cause);

        JobExecutionException failure = assertThrows(JobExecutionException.class, () -> this.job.execute(this.context));

        assertEquals(cause.getMessage(), failure.getMessage());
        assertSame(cause, failure.getCause());
        assertFalse(failure.isTerminal());
        verify(this.context, never()).setJobResult(any());
        verify(this.manifestManager).generateAndStoreManifestV2(consumer.getUuid());
        verifyNoMoreInteractions(this.manifestManager);
    }
}
