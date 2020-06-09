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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;

import org.junit.jupiter.api.Test;

import java.util.Date;

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
            assertEquals(source.getJobKey(), dto.getJobKey());
            assertEquals(source.getName(), dto.getName());
            assertEquals(source.getGroup(), dto.getGroup());
            assertEquals(source.getOrigin(), dto.getOrigin());
            assertEquals(source.getExecutor(), dto.getExecutor());
            assertEquals(source.getPrincipalName(), dto.getPrincipal());
            assertEquals(source.getStartTime(), dto.getStartTime());
            assertEquals(source.getEndTime(), dto.getEndTime());
            assertEquals(source.getJobResult(), dto.getResult());

            JobState state = source.getState();
            if (state != null) {
                assertEquals(state.name(), dto.getState());
            }
            else {
                assertNull(dto.getState());
            }

            JobState pstate = source.getPreviousState();
            if (pstate != null) {
                assertEquals(AsyncJobStatusTranslator.mapState(pstate).name(), dto.getPreviousState());
            }
            else {
                assertNull(dto.getPreviousState());
            }

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

    @Test
    public void mapsToFinished() {
        assertEquals(AsyncJobStatusTranslator.PublicJobState.FINISHED,
            AsyncJobStatusTranslator.mapState(JobState.FINISHED));
    }

    @Test
    public void mapsToCancelled() {
        assertEquals(AsyncJobStatusTranslator.PublicJobState.CANCELED,
            AsyncJobStatusTranslator.mapState(JobState.CANCELED));
    }

    @Test
    public void mapsToCreated() {
        assertEquals(AsyncJobStatusTranslator.PublicJobState.CREATED,
            AsyncJobStatusTranslator.mapState(JobState.CREATED));
        assertEquals(AsyncJobStatusTranslator.PublicJobState.CREATED,
            AsyncJobStatusTranslator.mapState(JobState.QUEUED));
    }

    @Test
    public void mapsToRunning() {
        assertEquals(AsyncJobStatusTranslator.PublicJobState.RUNNING,
            AsyncJobStatusTranslator.mapState(JobState.FAILED_WITH_RETRY));
        assertEquals(AsyncJobStatusTranslator.PublicJobState.RUNNING,
            AsyncJobStatusTranslator.mapState(JobState.RUNNING));
        assertEquals(AsyncJobStatusTranslator.PublicJobState.RUNNING,
            AsyncJobStatusTranslator.mapState(JobState.SCHEDULED));
        assertEquals(AsyncJobStatusTranslator.PublicJobState.RUNNING,
            AsyncJobStatusTranslator.mapState(JobState.WAITING));
    }

    @Test
    public void mapsToFailed() {
        assertEquals(AsyncJobStatusTranslator.PublicJobState.FAILED,
            AsyncJobStatusTranslator.mapState(JobState.ABORTED));
        assertEquals(AsyncJobStatusTranslator.PublicJobState.FAILED,
            AsyncJobStatusTranslator.mapState(JobState.FAILED));
    }
}
