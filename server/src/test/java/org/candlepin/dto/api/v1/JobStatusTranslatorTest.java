/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import static org.candlepin.pinsetter.core.model.JobStatus.*;
import static org.candlepin.pinsetter.core.model.JobStatus.TargetType.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.candlepin.auth.Principal;
import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.candlepin.pinsetter.core.model.JobStatus;

import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;

/**
 * Test suite for the JobStatusTranslator class
 */
public class JobStatusTranslatorTest
    extends AbstractTranslatorTest<JobStatus, JobStatusDTO, JobStatusTranslator> {

    protected JobStatusTranslator translator = new JobStatusTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, JobStatus.class, JobStatusDTO.class);
    }

    @Override
    protected JobStatusTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected JobStatus initSourceObject() {
        // JobStatus uses a JobDetail argument to populate fields instead
        // of setters, which is why we need to mock here.
        JobDetail jobDetail = mock(JobDetail.class);
        JobDataMap jobDataMap = mock(JobDataMap.class);
        Principal principal = mock(Principal.class);
        JobKey jobKey = new JobKey("test-name", "test-group");

        when(jobDetail.getKey()).thenReturn(jobKey);
        when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
        when(jobDataMap.get(PinsetterJobListener.PRINCIPAL_KEY)).thenReturn(principal);
        when(principal.getPrincipalName()).thenReturn("test-principal-name");
        when(jobDataMap.get(TARGET_TYPE)).thenReturn(CONSUMER);
        when(jobDataMap.get(TARGET_ID)).thenReturn("test-target-id");
        when(jobDataMap.get(OWNER_ID)).thenReturn("test-owner-id");
        when(jobDataMap.get(CORRELATION_ID)).thenReturn("test-correlation-id");

        JobStatus source = new JobStatus(jobDetail);
        source.setState(JobStatus.JobState.CREATED);
        source.setResult("result of job");
        source.setResultData("result data of job");

        return source;
    }

    @Override
    protected JobStatusDTO initDestinationObject() {
        return new JobStatusDTO();
    }

    @Override
    protected void verifyOutput(JobStatus source, JobStatusDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getState().toString(), dest.getState());
            assertEquals(source.getResult(), dest.getResult());
            assertEquals(source.getResultData(), dest.getResultData());
            assertEquals(source.getOwnerId(), dest.getOwnerId());
            assertEquals(source.getPrincipalName(), dest.getPrincipalName());
            assertEquals(source.getGroup(), dest.getGroup());
            assertEquals(source.getTargetId(), dest.getTargetId());
            assertEquals(source.getTargetType(), dest.getTargetType());
            assertEquals(source.getCorrelationId(), dest.getCorrelationId());
            assertEquals(source.getStartTime(), dest.getStartTime());
            assertEquals(source.getFinishTime(), dest.getFinishTime());
            assertEquals(source.isDone(), dest.isDone());
        }
        else {
            assertNull(dest);
        }
    }
}
