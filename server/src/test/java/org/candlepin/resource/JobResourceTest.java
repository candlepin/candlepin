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
package org.candlepin.resource;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.api.v1.JobStatusDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.JobCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.TransformedCandlepinQuery;
import org.candlepin.pinsetter.core.PinsetterException;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.pinsetter.core.model.JobStatus.JobState;
import org.candlepin.test.MockResultIterator;
import org.candlepin.util.ElementTransformer;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * JobResourceTest
 */
public class JobResourceTest {

    private JobResource jobResource;
    @Mock private JobCurator jobCurator;
    @Mock private OwnerCurator ownerCurator;
    @Mock private PinsetterKernel pinsetterKernel;
    @Mock private ConsumerTypeCurator consumerTypeCurator;
    @Mock private EnvironmentCurator environmentCurator;

    private I18n i18n;
    private ModelTranslator translator;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        translator = new StandardTranslator(this.consumerTypeCurator,
            this.environmentCurator,
            this.ownerCurator);
        jobResource = new JobResource(jobCurator, pinsetterKernel, i18n, translator);
    }

    private void mockCPQueryTransform(final CandlepinQuery query) {
        doAnswer(new Answer<CandlepinQuery>() {
            @Override
            public CandlepinQuery answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                return new TransformedCandlepinQuery(query, (ElementTransformer) args[0]);
            }
        }).when(query).transform(any(ElementTransformer.class));
    }

    @Test
    public void getStatusesNoArgs() {
        try {
            jobResource.getStatuses(null, null, null);
            fail("Should have thrown a BadRequestException");
        }
        catch (BadRequestException e) {
            //expected, return
            return;
        }
    }

    @Test
    public void schedulerPausedTest() throws PinsetterException {
        when(pinsetterKernel.getSchedulerStatus()).thenReturn(true);
        assertTrue(jobResource.getSchedulerStatus().isRunning());

        when(pinsetterKernel.getSchedulerStatus()).thenReturn(false);
        assertFalse(jobResource.getSchedulerStatus().isRunning());
    }

    @Test
    public void getStatusAndDeleteIfFinishedTest() {
        //nothing to delete..
        when(jobCurator.get("bogus_id")).thenReturn(new JobStatus());
        jobResource.getStatusAndDeleteIfFinished("foobar");
        verify(jobCurator, never()).delete(any(JobStatus.class));

        //now lets make a deletable JobStatus
        JobStatus finishedJobStatus = new JobStatus();
        finishedJobStatus.setState(JobState.FINISHED);
        when(jobCurator.get("deletable_id")).thenReturn(finishedJobStatus);
        jobResource.getStatusAndDeleteIfFinished("deletable_id");
        verify(jobCurator, atLeastOnce()).delete(finishedJobStatus);
    }

    @Test
    public void cancelJob() {
        //we are just testing that the cancellation gets into the db
        JobStatus createdJobStatus = new JobStatus();
        createdJobStatus.setState(JobState.CREATED);
        JobStatus canceledJobStatus = new JobStatus();
        canceledJobStatus.setState(JobState.CANCELED);

        when(jobCurator.get("cancel_id")).thenReturn(createdJobStatus);
        when(jobCurator.cancel("cancel_id")).thenReturn(canceledJobStatus);
        jobResource.cancel("cancel_id");
        verify(jobCurator, atLeastOnce()).cancel("cancel_id");
    }

    @Test
    public void getStatusesByPrincipal() {
        List<JobStatus> statuses = new ArrayList<>();
        JobStatus status = new JobStatus();
        statuses.add(status);

        CandlepinQuery query = mock(CandlepinQuery.class);
        when(query.list()).thenReturn(statuses);
        when(query.iterate()).thenReturn(new MockResultIterator(statuses.iterator()));
        when(query.iterate(anyInt(), anyBoolean())).thenReturn(new MockResultIterator(statuses.iterator()));
        when(jobCurator.findByPrincipalName(eq("admin"))).thenReturn(query);
        this.mockCPQueryTransform(query);

        Collection<JobStatusDTO> real = jobResource.getStatuses(null, null, "admin").list();
        assertNotNull(real);
        assertEquals(1, real.size());
    }

    @Test
    public void getStatusesByOwner() {
        List<JobStatus> statuses = new ArrayList<>();
        JobStatus status = new JobStatus();
        statuses.add(status);

        CandlepinQuery query = mock(CandlepinQuery.class);
        when(query.list()).thenReturn(statuses);
        when(query.iterate()).thenReturn(new MockResultIterator(statuses.iterator()));
        when(query.iterate(anyInt(), anyBoolean())).thenReturn(new MockResultIterator(statuses.iterator()));
        when(jobCurator.findByOwnerKey(eq("admin"))).thenReturn(query);
        this.mockCPQueryTransform(query);

        Collection<JobStatusDTO> real = jobResource.getStatuses("admin", null, null).list();
        assertNotNull(real);
        assertEquals(1, real.size());
    }

    @Test
    public void getStatusesByUuid() {
        List<JobStatus> statuses = new ArrayList<>();
        JobStatus status = new JobStatus();
        statuses.add(status);

        CandlepinQuery query = mock(CandlepinQuery.class);
        when(query.list()).thenReturn(statuses);
        when(query.iterate()).thenReturn(new MockResultIterator(statuses.iterator()));
        when(query.iterate(anyInt(), anyBoolean())).thenReturn(new MockResultIterator(statuses.iterator()));
        when(jobCurator.findByConsumerUuid(eq("abcd"))).thenReturn(query);
        this.mockCPQueryTransform(query);

        Collection<JobStatusDTO> real = jobResource.getStatuses(null, "abcd", null).list();
        assertNotNull(real);
        assertEquals(1, real.size());
    }

    @Test(expected = NotFoundException.class)
    public void statusForPrincipalNotFound() {
        when(jobCurator.findByPrincipalName(eq("foo"))).thenReturn(null);
        jobResource.getStatuses(null, null, "foo");
    }

    @Test(expected = NotFoundException.class)
    public void statusForOwnerNotFound() {
        when(jobCurator.findByOwnerKey(eq("foo"))).thenReturn(null);
        jobResource.getStatuses("foo", null, null);
    }

    @Test(expected = NotFoundException.class)
    public void statusForConsumerNotFound() {
        when(jobCurator.findByConsumerUuid(eq("foo"))).thenReturn(null);
        jobResource.getStatuses(null, "foo", null);
    }

    @Test(expected = BadRequestException.class)
    public void cannotSpecifyAllParams() {
        jobResource.getStatuses("fi", "fi", "fofum");
    }

    @Test(expected = BadRequestException.class)
    public void cannotSpecifyMoreThanOne() {
        jobResource.getStatuses("fi", "fi", null);
    }

    @Test
    public void emptyStringIsAlsoValid() {
        List<JobStatus> statuses = new ArrayList<>();
        JobStatus status = new JobStatus();
        statuses.add(status);

        CandlepinQuery query = mock(CandlepinQuery.class);
        when(query.list()).thenReturn(statuses);
        when(jobCurator.findByPrincipalName(eq("foo"))).thenReturn(query);
        when(query.transform(any(ElementTransformer.class))).thenReturn(query);

        jobResource.getStatuses(null, "", "foo");
    }

    /**
     * Returns true if a BadRequestException was thrown, otherwise
     * returns false.
     * @param o param1
     * @param c param2
     * @param p param3
     * @return true if a BadRequestException was thrown, otherwise
     * returns false.
     */
    private boolean expectException(String o, String c, String p) {
        try {
            jobResource.getStatuses(o, c, p);
        }
        catch (BadRequestException bre) {
            return false;
        }
        return true;
    }

    @Test
    public void verifyInput() {
        List<JobStatus> statuses = new ArrayList<>();
        JobStatus status = new JobStatus();
        statuses.add(status);

        CandlepinQuery query = mock(CandlepinQuery.class);
        when(query.list()).thenReturn(statuses);
        when(jobCurator.findByOwnerKey(any(String.class))).thenReturn(query);
        when(jobCurator.findByConsumerUuid(any(String.class))).thenReturn(query);
        when(jobCurator.findByPrincipalName(any(String.class))).thenReturn(query);
        when(query.transform(any(ElementTransformer.class))).thenReturn(query);

        assertFalse(expectException("owner", "uuid", "pname"));
        assertFalse(expectException("owner", null, "pname"));
        assertFalse(expectException("owner", "uuid", null));
        assertFalse(expectException(null, "uuid", "pname"));
        assertTrue(expectException("owner", null, null));
        assertTrue(expectException("owner", "", null));
        assertTrue(expectException(null, "uuid", null));
        assertTrue(expectException("", "uuid", null));
        assertTrue(expectException(null, null, "pname"));
        assertTrue(expectException(null, "", "pname"));
    }
}
