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
package org.candlepin.dto.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.resource.util.JobStateMapper.ExternalJobState;
import org.candlepin.util.Util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Date;
import java.util.stream.Stream;



/**
 * Test suite for the AsyncJobStatusTranslator class
 */
public class AsyncJobStatusTranslatorTest extends
    AbstractTranslatorTest<AsyncJobStatus, AsyncJobStatusDTO, AsyncJobStatusTranslator> {

    protected AsyncJobStatusTranslator translator = new AsyncJobStatusTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, AsyncJobStatus.class, AsyncJobStatusDTO.class);
    }

    @Override
    protected AsyncJobStatusTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected AsyncJobStatus initSourceObject() {
        AsyncJobStatus source = spy(new AsyncJobStatus());

        // We don't have a setId method, so we'll fake it for now. This may need to change in the
        // future when the ID generation changes.
        doReturn("job_id-1234567890").when(source).getId();
        doReturn(3).when(source).getAttempts();

        source.setJobKey("job_key");
        source.setName("job_name-8675309");
        source.setGroup("job_group");
        source.setOrigin("localhost_origin");
        source.setExecutor("localhost_exec");
        source.setPrincipalName("admin");
        source.setState(JobState.QUEUED);
        source.setState(JobState.RUNNING); // The second set should prime the previous state field
        source.setStartTime(new Date());
        source.setEndTime(new Date());
        source.setMaxAttempts(7);
        source.setJobResult("job_result");

        return source;
    }

    @Override
    protected AsyncJobStatusDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new AsyncJobStatusDTO();
    }

    @Override
    protected void verifyOutput(AsyncJobStatus source, AsyncJobStatusDTO dto, boolean childrenGenerated) {
        if (source != null) {
            // This DTO does not have any nested objects, so we don't need to worry about the
            // childrenGenerated flag

            assertEquals(source.getId(), dto.getId());
            assertEquals(source.getJobKey(), dto.getKey());
            assertEquals(source.getName(), dto.getName());
            assertEquals(source.getGroup(), dto.getGroup());
            assertEquals(source.getOrigin(), dto.getOrigin());
            assertEquals(source.getExecutor(), dto.getExecutor());
            assertEquals(source.getPrincipalName(), dto.getPrincipal());
            assertEquals(source.getStartTime(), Util.toDate(dto.getStartTime()));
            assertEquals(source.getEndTime(),  Util.toDate(dto.getEndTime()));
            assertEquals(source.getJobResult(), dto.getResultData());

            // Impl note: We test state translation explicitly in other tests

            Integer attempts = dto.getAttempts();
            if (attempts != null) {
                assertEquals(source.getAttempts(), attempts.intValue());
            }
            else {
                assertEquals(0, source.getAttempts());
            }

            Integer maxAttempts = dto.getMaxAttempts();
            if (maxAttempts != null) {
                assertEquals(source.getMaxAttempts(), maxAttempts.intValue());
            }
            else {
                assertEquals(0, source.getMaxAttempts());
            }
        }
        else {
            assertNull(dto);
        }
    }

    public static Stream<Arguments> translatedJobStateProvider() {
        return Stream.of(
            Arguments.of(null, null),

            Arguments.of(JobState.CREATED, ExternalJobState.CREATED.name()),
            Arguments.of(JobState.WAITING, ExternalJobState.CREATED.name()),
            Arguments.of(JobState.SCHEDULED, ExternalJobState.CREATED.name()),
            Arguments.of(JobState.QUEUED, ExternalJobState.CREATED.name()),

            Arguments.of(JobState.RUNNING, ExternalJobState.RUNNING.name()),
            Arguments.of(JobState.FAILED_WITH_RETRY, ExternalJobState.RUNNING.name()),

            Arguments.of(JobState.FINISHED, ExternalJobState.FINISHED.name()),

            Arguments.of(JobState.FAILED, ExternalJobState.FAILED.name()),
            Arguments.of(JobState.ABORTED, ExternalJobState.FAILED.name()),

            Arguments.of(JobState.CANCELED, ExternalJobState.CANCELED.name())
        );
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {1}")
    @MethodSource("translatedJobStateProvider")
    public void translatesJobStates(JobState input, String expected) {
        AsyncJobStatus status = mock(AsyncJobStatus.class);
        doReturn(input).when(status).getState();
        doReturn(input).when(status).getPreviousState();

        AsyncJobStatusDTO output = this.translator.translate(status);

        assertNotNull(output);
        assertEquals(expected, output.getState());
        assertEquals(expected, output.getPreviousState());
    }
}
