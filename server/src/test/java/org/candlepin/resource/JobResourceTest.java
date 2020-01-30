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
package org.candlepin.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
import org.candlepin.async.JobManager.ManagerState;
import org.candlepin.async.StateManagementException;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.SchedulerStatusDTO;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.model.AsyncJobStatusCurator.AsyncJobStatusQueryBuilder;
import org.candlepin.model.InvalidOrderKeyException;
import org.candlepin.model.Owner;
import org.candlepin.resource.util.JobStateMapper;
import org.candlepin.resource.util.JobStateMapper.ExternalJobState;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.Util;

import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;



/**
 * Test suite for the JobResource class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class JobResourceTest extends DatabaseTestFixture {

    private Configuration config;
    private I18n i18n;

    private JobManager jobManager;
    private AsyncJobStatusCurator jobCurator;


    @BeforeEach
    public void init() throws Exception {
        super.init();

        this.config = new CandlepinCommonTestConfig();
        this.i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
        this.jobManager = mock(JobManager.class);
        this.jobCurator = mock(AsyncJobStatusCurator.class);

        // Make sure we don't leave any page request on the context to muck with other tests
        ResteasyContext.popContextData(PageRequest.class);
    }

    private JobResource buildJobResource() {
        return new JobResource(this.config, this.i18n, this.modelTranslator, this.jobManager,
            this.jobCurator, this.ownerCurator);
    }

    public static Stream<Arguments> schedulerStatusTestArgProvider() {
        List<Arguments> args = new LinkedList<>();

        for (ManagerState state : ManagerState.values()) {
            args.add(Arguments.of(state, state == ManagerState.RUNNING));
        }

        return args.stream();
    }

    public static Stream<Arguments> emptyInputProvider() {
        return Stream.of(
            Arguments.of((String) null),
            Arguments.of(""));
    }

    @ParameterizedTest
    @EnumSource(ManagerState.class)
    public void testGetSchedulerStatus(ManagerState state) {
        doReturn(state).when(this.jobManager).getManagerState();
        boolean expected = (state == ManagerState.RUNNING);

        JobResource resource = this.buildJobResource();
        SchedulerStatusDTO output = resource.getSchedulerStatus();

        assertNotNull(output);
        assertEquals(expected, output.isRunning());
    }

    @Test
    public void testSetSchedulerStatusToRunning() {
        doReturn(ManagerState.RUNNING).when(this.jobManager).getManagerState();

        JobResource resource = this.buildJobResource();
        SchedulerStatusDTO output = resource.setSchedulerStatus(true);

        assertNotNull(output);
        assertTrue(output.isRunning());

        verify(this.jobManager, times(1)).resume();
    }

    @Test
    public void testSetSchedulerStatusToPaused() {
        doReturn(ManagerState.SUSPENDED).when(this.jobManager).getManagerState();

        JobResource resource = this.buildJobResource();
        SchedulerStatusDTO output = resource.setSchedulerStatus(false);

        assertNotNull(output);
        assertFalse(output.isRunning());

        verify(this.jobManager, times(1)).suspend();
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "false" })
    public void testSetSchedulerIllegalStateExceptionsAreMaskedWithIseExceptions(boolean running) {
        doThrow(new IllegalStateException("testing start op failure")).when(this.jobManager).resume();
        doThrow(new IllegalStateException("testing suspend op failure")).when(this.jobManager).suspend();

        JobResource resource = this.buildJobResource();
        assertThrows(IseException.class, () -> resource.setSchedulerStatus(running));
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "false" })
    public void testSetSchedulerStateManagementExceptionsAreMaskedWithIseExceptions(boolean running) {
        doThrow(new StateManagementException(ManagerState.SUSPENDED, ManagerState.RUNNING,
            "testing start op failure")).when(this.jobManager).resume();
        doThrow(new StateManagementException(ManagerState.RUNNING, ManagerState.SUSPENDED,
            "testing suspend op failure")).when(this.jobManager).suspend();

        JobResource resource = this.buildJobResource();
        assertThrows(IseException.class, () -> resource.setSchedulerStatus(running));
    }

    public static Stream<Arguments> targetJobStatusesSimpleCollectionProvider() {
        return Stream.of(
            Arguments.of((Set) null),
            Arguments.of(Util.asSet()),
            Arguments.of(Util.asSet((String) null)),
            Arguments.of(Util.asSet("")),
            Arguments.of(Util.asSet("value-1")),
            Arguments.of(Util.asSet("value-1", "value-2", "value-3")));
    }

    public static Stream<Arguments> targetJobStatusesWithJobStatesProvider() {
        List<Arguments> args = new LinkedList<>();

        List<Set<ExternalJobState>> stateBlocks = new LinkedList<>();

        for (int i = 1; i <= 3; ++i) {
            ExternalJobState[] states = ExternalJobState.values();
            Set<ExternalJobState> block = new HashSet<>();

            for (int j = 0; j < i; ++j) {
                block.add(states[j]);
            }

            stateBlocks.add(block);
        }

        // Mixed case converter
        Function<ExternalJobState, String> mixedCaseMapper = (ExternalJobState state) -> {
            String name = state.name();

            StringBuilder builder = new StringBuilder(name.length());
            boolean upper = true;

            for (char chr : name.toCharArray()) {
                builder.append(upper ? Character.toUpperCase(chr) : chr);
                upper = !upper;
            }

            return builder.toString();
        };

        // Process the blocks, adding lower case, upper case and mixed case
        // variants to the arg list
        for (Set<ExternalJobState> block : stateBlocks) {
            Set<String> names;

            // Lower case
            names = block.stream()
                .map(state -> state.name().toLowerCase())
                .collect(Collectors.toSet());

            args.add(Arguments.of(names, JobStateMapper.translateStates(block)));

            // Upper case
            names = block.stream()
                .map(state -> state.name().toUpperCase())
                .collect(Collectors.toSet());

            args.add(Arguments.of(names, JobStateMapper.translateStates(block)));

            // Mixed case
            names = block.stream()
                .map(mixedCaseMapper)
                .collect(Collectors.toSet());

            args.add(Arguments.of(names, JobStateMapper.translateStates(block)));
        }

        return args.stream();
    }

    public static Stream<Arguments> targetJobStatusesWithInvalidJobStatesProvider() {
        return Stream.of(
            Arguments.of((String) null),
            Arguments.of(""),
            Arguments.of("bad job state"),

            // Internal job states should be considered "invalid"
            Arguments.of("WAITING"),
            Arguments.of("SCHEDULED"),
            Arguments.of("QUEUED"),
            Arguments.of("FAILED_WITH_RETRY"),
            Arguments.of("ABORTED"));
    }

    public static Stream<Arguments> targetJobStatusesByDatesProvider() {
        return Stream.of(
            Arguments.of((Date) null),
            Arguments.of(new Date()),
            Arguments.of(Util.yesterday()),
            Arguments.of(Util.tomorrow()));
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesSimpleCollectionProvider")
    public void testListJobStatusesByJobId(Set<String> ids) {
        AsyncJobStatus status1 = mock(AsyncJobStatus.class);
        AsyncJobStatus status2 = mock(AsyncJobStatus.class);
        AsyncJobStatus status3 = mock(AsyncJobStatus.class);
        List<AsyncJobStatus> expected = Arrays.asList(status1, status2, status3);

        doReturn(expected).when(this.jobManager).findJobs(any(AsyncJobStatusQueryBuilder.class));

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        Stream<AsyncJobStatusDTO> result = resource.listJobStatuses(ids, null, null, null, null,
            null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.jobManager, times(1)).findJobs(captor.capture());
        AsyncJobStatusQueryBuilder builder = captor.getValue();

        assertNotNull(builder);
        assertEquals(ids, builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getJobStates());
        assertNull(builder.getOwnerIds());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());
        assertNull(builder.getOffset());
        assertNull(builder.getLimit());
        assertNull(builder.getOrder());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesSimpleCollectionProvider")
    public void testListJobStatusesByJobKey(Set<String> values) {
        AsyncJobStatus status1 = mock(AsyncJobStatus.class);
        AsyncJobStatus status2 = mock(AsyncJobStatus.class);
        AsyncJobStatus status3 = mock(AsyncJobStatus.class);
        List<AsyncJobStatus> expected = Arrays.asList(status1, status2, status3);

        doReturn(expected).when(this.jobManager).findJobs(any(AsyncJobStatusQueryBuilder.class));

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        Stream<AsyncJobStatusDTO> result = resource.listJobStatuses(null, values, null, null, null,
            null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.jobManager, times(1)).findJobs(captor.capture());
        AsyncJobStatusQueryBuilder builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertEquals(values, builder.getJobKeys());
        assertNull(builder.getJobStates());
        assertNull(builder.getOwnerIds());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());
        assertNull(builder.getOffset());
        assertNull(builder.getLimit());
        assertNull(builder.getOrder());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }


    @ParameterizedTest
    @MethodSource("targetJobStatusesWithJobStatesProvider")
    public void testListJobStatusesWithJobStates(Set<String> stateNames, Set<JobState> expected) {
        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        Stream<AsyncJobStatusDTO> result = resource.listJobStatuses(null, null, stateNames, null, null,
            null, null, null, null);

        verify(this.jobManager, times(1)).findJobs(captor.capture());
        AsyncJobStatusQueryBuilder builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getOwnerIds());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());
        assertNull(builder.getOffset());
        assertNull(builder.getLimit());
        assertNull(builder.getOrder());

        Collection<JobState> states = builder.getJobStates();
        assertNotNull(states);
        assertEquals(expected, states);
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesWithInvalidJobStatesProvider")
    public void testListJobStatusesWithInvalidJobStates(String badStateName) {
        Set<String> stateNames = Util.asSet(badStateName);

        JobResource resource = this.buildJobResource();
        assertThrows(NotFoundException.class, () ->
            resource.listJobStatuses(null, null, stateNames, null, null, null, null, null, null));
    }

    @Test
    public void testListJobStatusesWithValidOwnerKeys() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        Set<String> keys = Util.asSet(owner1.getKey(), owner2.getKey(), owner3.getKey());

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        Stream<AsyncJobStatusDTO> result = resource.listJobStatuses(null, null, null, keys, null,
            null, null, null, null);

        verify(this.jobManager, times(1)).findJobs(captor.capture());
        AsyncJobStatusQueryBuilder builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getJobStates());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());
        assertNull(builder.getOffset());
        assertNull(builder.getLimit());
        assertNull(builder.getOrder());

        Collection<String> ownerIds = builder.getOwnerIds();
        assertNotNull(ownerIds);
        assertEquals(3, ownerIds.size());
        assertTrue(ownerIds.contains(owner1.getId()));
        assertTrue(ownerIds.contains(owner2.getId()));
        assertTrue(ownerIds.contains(owner3.getId()));
    }

    @Test
    public void testListJobStatusesWithInvalidOwnerKeys() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Set<String> keys = Util.asSet("", owner1.getKey(), "bad_key", owner2.getKey(), null);
        Set<String> expected = Util.asSet("", owner1.getId(), "bad_key", owner2.getId(), null);

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        Stream<AsyncJobStatusDTO> result = resource.listJobStatuses(null, null, null, keys, null,
            null, null, null, null);

        verify(this.jobManager, times(1)).findJobs(captor.capture());
        AsyncJobStatusQueryBuilder builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getJobStates());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());
        assertNull(builder.getOffset());
        assertNull(builder.getLimit());
        assertNull(builder.getOrder());

        Collection<String> ownerIds = builder.getOwnerIds();
        assertNotNull(ownerIds);

        assertEquals(expected.size(), ownerIds.size());
        for (String id : expected) {
            assertTrue(ownerIds.contains(id));
        }
    }

    @Test
    public void testListJobStatusesConvertsNullOwnerKeyStringToNullRef() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Set<String> keys = Util.asSet(owner1.getKey(), "null", owner2.getKey());
        Set<String> expected = Util.asSet(owner1.getId(), null, owner2.getId());

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        Stream<AsyncJobStatusDTO> result = resource.listJobStatuses(null, null, null, keys, null,
            null, null, null, null);

        verify(this.jobManager, times(1)).findJobs(captor.capture());
        AsyncJobStatusQueryBuilder builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getJobStates());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());
        assertNull(builder.getOffset());
        assertNull(builder.getLimit());
        assertNull(builder.getOrder());

        Collection<String> ownerIds = builder.getOwnerIds();
        assertNotNull(ownerIds);

        assertEquals(expected.size(), ownerIds.size());
        for (String id : expected) {
            assertTrue(ownerIds.contains(id));
        }
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesSimpleCollectionProvider")
    public void testListJobStatusesByJobPrincipal(Set<String> values) {
        AsyncJobStatus status1 = mock(AsyncJobStatus.class);
        AsyncJobStatus status2 = mock(AsyncJobStatus.class);
        AsyncJobStatus status3 = mock(AsyncJobStatus.class);
        List<AsyncJobStatus> expected = Arrays.asList(status1, status2, status3);

        doReturn(expected).when(this.jobManager).findJobs(any(AsyncJobStatusQueryBuilder.class));

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        Stream<AsyncJobStatusDTO> result = resource.listJobStatuses(null, null, null, null, values,
            null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.jobManager, times(1)).findJobs(captor.capture());
        AsyncJobStatusQueryBuilder builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getJobStates());
        assertNull(builder.getOwnerIds());
        assertEquals(values, builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());
        assertNull(builder.getOffset());
        assertNull(builder.getLimit());
        assertNull(builder.getOrder());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesSimpleCollectionProvider")
    public void testListJobStatusesByJobOrigin(Set<String> values) {
        AsyncJobStatus status1 = mock(AsyncJobStatus.class);
        AsyncJobStatus status2 = mock(AsyncJobStatus.class);
        AsyncJobStatus status3 = mock(AsyncJobStatus.class);
        List<AsyncJobStatus> expected = Arrays.asList(status1, status2, status3);

        doReturn(expected).when(this.jobManager).findJobs(any(AsyncJobStatusQueryBuilder.class));

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        Stream<AsyncJobStatusDTO> result = resource.listJobStatuses(null, null, null, null, null,
            values, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.jobManager, times(1)).findJobs(captor.capture());
        AsyncJobStatusQueryBuilder builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getJobStates());
        assertNull(builder.getOwnerIds());
        assertNull(builder.getPrincipalNames());
        assertEquals(values, builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());
        assertNull(builder.getOffset());
        assertNull(builder.getLimit());
        assertNull(builder.getOrder());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesSimpleCollectionProvider")
    public void testListJobStatusesByJobExecutor(Set<String> values) {
        AsyncJobStatus status1 = mock(AsyncJobStatus.class);
        AsyncJobStatus status2 = mock(AsyncJobStatus.class);
        AsyncJobStatus status3 = mock(AsyncJobStatus.class);
        List<AsyncJobStatus> expected = Arrays.asList(status1, status2, status3);

        doReturn(expected).when(this.jobManager).findJobs(any(AsyncJobStatusQueryBuilder.class));

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        Stream<AsyncJobStatusDTO> result = resource.listJobStatuses(null, null, null, null, null,
            null, values, null, null);

        // Verify the input passthrough is working properly
        verify(this.jobManager, times(1)).findJobs(captor.capture());
        AsyncJobStatusQueryBuilder builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getJobStates());
        assertNull(builder.getOwnerIds());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertEquals(values, builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());
        assertNull(builder.getOffset());
        assertNull(builder.getLimit());
        assertNull(builder.getOrder());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesByDatesProvider")
    public void testListJobStatusesByJobStartDate(Date value) {
        AsyncJobStatus status1 = mock(AsyncJobStatus.class);
        AsyncJobStatus status2 = mock(AsyncJobStatus.class);
        AsyncJobStatus status3 = mock(AsyncJobStatus.class);
        List<AsyncJobStatus> expected = Arrays.asList(status1, status2, status3);

        doReturn(expected).when(this.jobManager).findJobs(any(AsyncJobStatusQueryBuilder.class));

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        Stream<AsyncJobStatusDTO> result = resource.listJobStatuses(null, null, null, null, null,
            null, null, value, null);

        // Verify the input passthrough is working properly
        verify(this.jobManager, times(1)).findJobs(captor.capture());
        AsyncJobStatusQueryBuilder builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getJobStates());
        assertNull(builder.getOwnerIds());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertEquals(value, builder.getStartDate());
        assertNull(builder.getEndDate());
        assertNull(builder.getOffset());
        assertNull(builder.getLimit());
        assertNull(builder.getOrder());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesByDatesProvider")
    public void testListJobStatusesByJobEndDate(Date value) {
        AsyncJobStatus status1 = mock(AsyncJobStatus.class);
        AsyncJobStatus status2 = mock(AsyncJobStatus.class);
        AsyncJobStatus status3 = mock(AsyncJobStatus.class);
        List<AsyncJobStatus> expected = Arrays.asList(status1, status2, status3);

        doReturn(expected).when(this.jobManager).findJobs(any(AsyncJobStatusQueryBuilder.class));

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        Stream<AsyncJobStatusDTO> result = resource.listJobStatuses(null, null, null, null, null,
            null, null, null, value);

        // Verify the input passthrough is working properly
        verify(this.jobManager, times(1)).findJobs(captor.capture());
        AsyncJobStatusQueryBuilder builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getJobStates());
        assertNull(builder.getOwnerIds());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertEquals(value, builder.getEndDate());
        assertNull(builder.getOffset());
        assertNull(builder.getLimit());
        assertNull(builder.getOrder());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    public void testListJobStatusesCanPageResults() {
        AsyncJobStatus status1 = mock(AsyncJobStatus.class);
        AsyncJobStatus status2 = mock(AsyncJobStatus.class);
        AsyncJobStatus status3 = mock(AsyncJobStatus.class);
        List<AsyncJobStatus> expected = Arrays.asList(status1, status2, status3);

        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(2);
        pageRequest.setPerPage(5);

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        int expectedOffset = (pageRequest.getPage() - 1) * pageRequest.getPerPage();

        doReturn(expected).when(this.jobManager).findJobs(any(AsyncJobStatusQueryBuilder.class));

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        Stream<AsyncJobStatusDTO> result = resource.listJobStatuses(null, null, null, null, null,
            null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.jobManager, times(1)).findJobs(captor.capture());
        AsyncJobStatusQueryBuilder builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getJobStates());
        assertNull(builder.getOwnerIds());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());
        assertEquals(expectedOffset, builder.getOffset());
        assertEquals(pageRequest.getPerPage(), builder.getLimit());
        assertNull(builder.getOrder());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @ParameterizedTest
    @ValueSource(strings = { "null", "true", "false" })
    public void testListJobStatusesCanOrderResults(Boolean reverseOrder) {
        AsyncJobStatus status1 = mock(AsyncJobStatus.class);
        AsyncJobStatus status2 = mock(AsyncJobStatus.class);
        AsyncJobStatus status3 = mock(AsyncJobStatus.class);
        List<AsyncJobStatus> expected = Arrays.asList(status1, status2, status3);

        PageRequest pageRequest = new PageRequest();
        pageRequest.setSortBy("id");

        if (reverseOrder != null) {
            pageRequest.setOrder(reverseOrder ? PageRequest.Order.DESCENDING : PageRequest.Order.ASCENDING);
        }

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        doReturn(expected).when(this.jobManager).findJobs(any(AsyncJobStatusQueryBuilder.class));

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        Stream<AsyncJobStatusDTO> result = resource.listJobStatuses(null, null, null, null, null,
            null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.jobManager, times(1)).findJobs(captor.capture());
        AsyncJobStatusQueryBuilder builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getJobStates());
        assertNull(builder.getOwnerIds());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());
        assertNull(builder.getOffset());
        assertNull(builder.getLimit());

        assertNotNull(builder.getOrder());
        assertEquals(1, builder.getOrder().size());

        AsyncJobStatusQueryBuilder.Order queryOrder = builder.getOrder().iterator().next();
        assertNotNull(queryOrder);
        assertEquals(pageRequest.getSortBy(), queryOrder.column());
        assertEquals(pageRequest.getOrder() == PageRequest.Order.DESCENDING, queryOrder.reverse());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @Test
    public void testListJobStatusesFailsOnInvalidDateRange() {
        Date start = Util.addDaysToDt(-2);
        Date end = Util.addDaysToDt(2);

        JobResource resource = this.buildJobResource();
        assertThrows(BadRequestException.class, () ->
            resource.listJobStatuses(null, null, null, null, null, null, null, end, start));
    }

    @Test
    public void testListJobStatusesFailsOnInvalidOrderKey() {
        // This doesn't actually matter since we're mocking the exception, but we'll do it anyway
        // for completeness
        PageRequest pageRequest = new PageRequest();
        pageRequest.setSortBy("bad_key");

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        doThrow(new InvalidOrderKeyException()).when(this.jobManager).findJobs(any());

        JobResource resource = this.buildJobResource();
        assertThrows(BadRequestException.class, () ->
            resource.listJobStatuses(null, null, null, null, null, null, null, null, null));
    }

    @Test
    public void testListJobStatusesFailsWhenResultMaxLimitExceeded() {
        doReturn(10001L).when(this.jobCurator).getJobCount(any(AsyncJobStatusQueryBuilder.class));

        JobResource resource = this.buildJobResource();
        assertThrows(BadRequestException.class, () ->
            resource.listJobStatuses(null, null, null, null, null, null, null, null, null));
    }

    @Test
    public void testListJobStatusesDoesNotFailWhenResultMaxLimitNotExceeded() {
        doReturn(10000L).when(this.jobCurator).getJobCount(any(AsyncJobStatusQueryBuilder.class));

        JobResource resource = this.buildJobResource();
        assertDoesNotThrow(() ->
            resource.listJobStatuses(null, null, null, null, null, null, null, null, null));
    }

    @Test
    public void testGetJobStatus() {
        String jobId = "test_job_id";

        AsyncJobStatus status = mock(AsyncJobStatus.class);
        doReturn(jobId).when(status).getId();
        doReturn(status).when(this.jobManager).findJob(eq(jobId));

        JobResource resource = this.buildJobResource();
        AsyncJobStatusDTO output = resource.getJobStatus(jobId);

        assertNotNull(output);
        assertEquals(jobId, output.getId());
    }

    @Test
    public void testGetJobStatusWithBadId() {
        String jobId = "bad_job_id";

        JobResource resource = this.buildJobResource();
        assertThrows(NotFoundException.class, () -> resource.getJobStatus(jobId));
    }

    @ParameterizedTest
    @MethodSource("emptyInputProvider")
    public void testGetJobStatusWithBadId(String jobId) {
        JobResource resource = this.buildJobResource();
        assertThrows(BadRequestException.class, () -> resource.getJobStatus(jobId));
    }

    @Test
    public void testCancelJob() {
        String jobId = "test_job_id";

        AsyncJobStatus status = mock(AsyncJobStatus.class);
        doReturn(jobId).when(status).getId();
        doReturn(status).when(this.jobManager).cancelJob(eq(jobId));

        JobResource resource = this.buildJobResource();
        AsyncJobStatusDTO output = resource.cancelJob(jobId);

        assertNotNull(output);
        assertEquals(jobId, output.getId());

        // This is implied due to the output matching, but it doesn't hurt, either.
        verify(this.jobManager, times(1)).cancelJob(eq(jobId));
    }

    @Test
    public void testCancelJobWithBadId() {
        String jobId = "bad_job_id";

        JobResource resource = this.buildJobResource();
        assertThrows(NotFoundException.class, () -> resource.cancelJob(jobId));
    }

    @ParameterizedTest
    @MethodSource("emptyInputProvider")
    public void testCancelJobWithBadId(String jobId) {
        JobResource resource = this.buildJobResource();
        assertThrows(BadRequestException.class, () -> resource.cancelJob(jobId));
    }

    @Test
    public void testCancelJobInTerminalState() {
        String jobId = "test_job_id";
        doThrow(new IllegalStateException()).when(this.jobManager).cancelJob(eq(jobId));

        JobResource resource = this.buildJobResource();
        assertThrows(BadRequestException.class, () -> resource.cancelJob(jobId));
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesSimpleCollectionProvider")
    public void testCleanupTerminalJobsByIds(Set<String> values) {
        this.testCleanupTerminalJobsByIds(values, false);
        clearInvocations(this.jobManager, this.jobCurator);
        this.testCleanupTerminalJobsByIds(values, true);
    }

    public void testCleanupTerminalJobsByIds(Set<String> values, boolean force) {
        int expected = new Random().nextInt();
        doReturn(expected).when(this.jobManager).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
        doReturn(expected).when(this.jobCurator).deleteJobs(any(AsyncJobStatusQueryBuilder.class));

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        int result = resource.cleanupTerminalJobs(values, null, null, null, null,
            null, null, null, null, force);

        // Verify the input passthrough is working properly
        if (force) {
            verify(this.jobManager, never()).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
            verify(this.jobCurator, times(1)).deleteJobs(captor.capture());
        }
        else {
            verify(this.jobManager, times(1)).cleanupJobs(captor.capture());
            verify(this.jobCurator, never()).deleteJobs(any(AsyncJobStatusQueryBuilder.class));
        }

        AsyncJobStatusQueryBuilder builder = captor.getValue();
        assertNotNull(builder);
        assertEquals(values, builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getOwnerIds());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());

        // Verify the output passthrough is working properly
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesSimpleCollectionProvider")
    public void testCleanupTerminalJobsByKeys(Set<String> values) {
        this.testCleanupTerminalJobsByKeys(values, false);
        clearInvocations(this.jobManager, this.jobCurator);
        this.testCleanupTerminalJobsByKeys(values, true);
    }

    public void testCleanupTerminalJobsByKeys(Set<String> values, boolean force) {
        int expected = new Random().nextInt();
        doReturn(expected).when(this.jobManager).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
        doReturn(expected).when(this.jobCurator).deleteJobs(any(AsyncJobStatusQueryBuilder.class));

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        int result = resource.cleanupTerminalJobs(null, values, null, null, null,
            null, null, null, null, force);

        // Verify the input passthrough is working properly
        if (force) {
            verify(this.jobManager, never()).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
            verify(this.jobCurator, times(1)).deleteJobs(captor.capture());
        }
        else {
            verify(this.jobManager, times(1)).cleanupJobs(captor.capture());
            verify(this.jobCurator, never()).deleteJobs(any(AsyncJobStatusQueryBuilder.class));
        }

        AsyncJobStatusQueryBuilder builder = captor.getValue();
        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertEquals(values, builder.getJobKeys());
        assertNull(builder.getOwnerIds());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());

        // Verify the output passthrough is working properly
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesWithJobStatesProvider")
    public void testCleanupTerminalJobsByJobStates(Set<String> stateNames, Set<JobState> expected) {
        this.testCleanupTerminalJobsWithJobStates(stateNames, expected, false);
        clearInvocations(this.jobManager, this.jobCurator);
        this.testCleanupTerminalJobsWithJobStates(stateNames, expected, true);
    }

    public void testCleanupTerminalJobsWithJobStates(Set<String> stateNames, Set<JobState> expected,
        boolean force) {

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        int result = resource.cleanupTerminalJobs(null, null, stateNames, null, null, null, null,
            null, null, force);

        if (force) {
            verify(this.jobManager, never()).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
            verify(this.jobCurator, times(1)).deleteJobs(captor.capture());
        }
        else {
            verify(this.jobManager, times(1)).cleanupJobs(captor.capture());
            verify(this.jobCurator, never()).deleteJobs(any(AsyncJobStatusQueryBuilder.class));
        }

        AsyncJobStatusQueryBuilder builder = captor.getValue();
        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getOwnerIds());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());

        Collection<JobState> states = builder.getJobStates();
        assertNotNull(states);
        assertEquals(expected, states);
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesWithInvalidJobStatesProvider")
    public void testCleanupTerminalJobsWithInvalidJobStates(String badStateName) {
        Set<String> stateNames = new HashSet<>();
        stateNames.add(badStateName);

        JobResource resource = this.buildJobResource();
        assertThrows(NotFoundException.class, () ->
            resource.cleanupTerminalJobs(null, null, stateNames, null, null, null, null, null, null, false));
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesWithInvalidJobStatesProvider")
    public void testCleanupTerminalJobsWithInvalidJobStatesWhenForced(String badStateName) {
        Set<String> stateNames = new HashSet<>();
        stateNames.add(badStateName);

        JobResource resource = this.buildJobResource();
        assertThrows(NotFoundException.class, () ->
            resource.cleanupTerminalJobs(null, null, stateNames, null, null, null, null, null, null, true));
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "false" })
    public void testCleanupTerminalJobsWithValidOwnerKeys(boolean force) {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        Set<String> keys = Util.asSet(owner1.getKey(), owner2.getKey(), owner3.getKey());

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        int result = resource.cleanupTerminalJobs(null, null, null, keys, null, null,
            null, null, null, force);

        if (force) {
            verify(this.jobManager, never()).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
            verify(this.jobCurator, times(1)).deleteJobs(captor.capture());
        }
        else {
            verify(this.jobManager, times(1)).cleanupJobs(captor.capture());
            verify(this.jobCurator, never()).deleteJobs(any(AsyncJobStatusQueryBuilder.class));
        }
        AsyncJobStatusQueryBuilder builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getJobStates());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());

        Collection<String> ownerIds = builder.getOwnerIds();
        assertNotNull(ownerIds);
        assertEquals(3, ownerIds.size());
        assertTrue(ownerIds.contains(owner1.getId()));
        assertTrue(ownerIds.contains(owner2.getId()));
        assertTrue(ownerIds.contains(owner3.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "false" })
    public void testCleanupTerminalJobsWithInvalidOwnerKeys(boolean force) {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Set<String> keys = Util.asSet("", owner1.getKey(), "key", owner2.getKey(), null);
        Set<String> expected = Util.asSet("", owner1.getId(), "key", owner2.getId(), null);

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        int result = resource.cleanupTerminalJobs(null, null, null, keys, null, null,
            null, null, null, force);

        if (force) {
            verify(this.jobManager, never()).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
            verify(this.jobCurator, times(1)).deleteJobs(captor.capture());
        }
        else {
            verify(this.jobManager, times(1)).cleanupJobs(captor.capture());
            verify(this.jobCurator, never()).deleteJobs(any(AsyncJobStatusQueryBuilder.class));
        }
        AsyncJobStatusQueryBuilder builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getJobStates());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());

        Collection<String> ownerIds = builder.getOwnerIds();
        assertNotNull(ownerIds);

        assertEquals(expected.size(), ownerIds.size());
        for (String id : expected) {
            assertTrue(ownerIds.contains(id));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "false" })
    public void testCleanupTerminalJobsConvertsNullOwnerKeyStringToNullRef(boolean force) {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Set<String> keys = Util.asSet(owner1.getKey(), "null", owner2.getKey());
        Set<String> expected = Util.asSet(owner1.getId(), null, owner2.getId());

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        int result = resource.cleanupTerminalJobs(null, null, null, keys, null, null,
            null, null, null, force);

        if (force) {
            verify(this.jobManager, never()).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
            verify(this.jobCurator, times(1)).deleteJobs(captor.capture());
        }
        else {
            verify(this.jobManager, times(1)).cleanupJobs(captor.capture());
            verify(this.jobCurator, never()).deleteJobs(any(AsyncJobStatusQueryBuilder.class));
        }

        AsyncJobStatusQueryBuilder builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getJobStates());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());

        Collection<String> ownerIds = builder.getOwnerIds();
        assertNotNull(ownerIds);

        assertEquals(expected.size(), ownerIds.size());
        for (String id : expected) {
            assertTrue(ownerIds.contains(id));
        }
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesSimpleCollectionProvider")
    public void testCleanupTerminalJobsByPrincipals(Set<String> values) {
        this.testCleanupTerminalJobsByPrincipals(values, false);
        clearInvocations(this.jobManager, this.jobCurator);
        this.testCleanupTerminalJobsByPrincipals(values, true);
    }

    public void testCleanupTerminalJobsByPrincipals(Set<String> values, boolean force) {
        int expected = new Random().nextInt();
        doReturn(expected).when(this.jobManager).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
        doReturn(expected).when(this.jobCurator).deleteJobs(any(AsyncJobStatusQueryBuilder.class));

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        int result = resource.cleanupTerminalJobs(null, null, null, null, values,
            null, null, null, null, force);

        // Verify the input passthrough is working properly
        if (force) {
            verify(this.jobManager, never()).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
            verify(this.jobCurator, times(1)).deleteJobs(captor.capture());
        }
        else {
            verify(this.jobManager, times(1)).cleanupJobs(captor.capture());
            verify(this.jobCurator, never()).deleteJobs(any(AsyncJobStatusQueryBuilder.class));
        }

        AsyncJobStatusQueryBuilder builder = captor.getValue();
        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getOwnerIds());
        assertEquals(values, builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());

        // Verify the output passthrough is working properly
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesSimpleCollectionProvider")
    public void testCleanupTerminalJobsByOrigins(Set<String> values) {
        this.testCleanupTerminalJobsByOrigins(values, false);
        clearInvocations(this.jobManager, this.jobCurator);
        this.testCleanupTerminalJobsByOrigins(values, true);
    }

    public void testCleanupTerminalJobsByOrigins(Set<String> values, boolean force) {
        int expected = new Random().nextInt();
        doReturn(expected).when(this.jobManager).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
        doReturn(expected).when(this.jobCurator).deleteJobs(any(AsyncJobStatusQueryBuilder.class));

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        int result = resource.cleanupTerminalJobs(null, null, null, null, null,
            values, null, null, null, force);

        // Verify the input passthrough is working properly
        if (force) {
            verify(this.jobManager, never()).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
            verify(this.jobCurator, times(1)).deleteJobs(captor.capture());
        }
        else {
            verify(this.jobManager, times(1)).cleanupJobs(captor.capture());
            verify(this.jobCurator, never()).deleteJobs(any(AsyncJobStatusQueryBuilder.class));
        }

        AsyncJobStatusQueryBuilder builder = captor.getValue();
        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getOwnerIds());
        assertNull(builder.getPrincipalNames());
        assertEquals(values, builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());

        // Verify the output passthrough is working properly
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesSimpleCollectionProvider")
    public void testCleanupTerminalJobsByExecutors(Set<String> values) {
        this.testCleanupTerminalJobsByExecutors(values, false);
        clearInvocations(this.jobManager, this.jobCurator);
        this.testCleanupTerminalJobsByExecutors(values, true);
    }

    public void testCleanupTerminalJobsByExecutors(Set<String> values, boolean force) {
        int expected = new Random().nextInt();
        doReturn(expected).when(this.jobManager).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
        doReturn(expected).when(this.jobCurator).deleteJobs(any(AsyncJobStatusQueryBuilder.class));

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        int result = resource.cleanupTerminalJobs(null, null, null, null, null,
            null, values, null, null, force);

        // Verify the input passthrough is working properly
        if (force) {
            verify(this.jobManager, never()).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
            verify(this.jobCurator, times(1)).deleteJobs(captor.capture());
        }
        else {
            verify(this.jobManager, times(1)).cleanupJobs(captor.capture());
            verify(this.jobCurator, never()).deleteJobs(any(AsyncJobStatusQueryBuilder.class));
        }

        AsyncJobStatusQueryBuilder builder = captor.getValue();
        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getOwnerIds());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertEquals(values, builder.getExecutors());
        assertNull(builder.getStartDate());
        assertNull(builder.getEndDate());

        // Verify the output passthrough is working properly
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesByDatesProvider")
    public void testCleanupTerminalJobsByStartDate(Date value) {
        this.testCleanupTerminalJobsByStartDate(value, false);
        clearInvocations(this.jobManager, this.jobCurator);
        this.testCleanupTerminalJobsByStartDate(value, true);
    }

    public void testCleanupTerminalJobsByStartDate(Date value, boolean force) {
        int expected = new Random().nextInt();
        doReturn(expected).when(this.jobManager).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
        doReturn(expected).when(this.jobCurator).deleteJobs(any(AsyncJobStatusQueryBuilder.class));

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        int result = resource.cleanupTerminalJobs(null, null, null, null, null,
            null, null, value, null, force);

        // Verify the input passthrough is working properly
        if (force) {
            verify(this.jobManager, never()).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
            verify(this.jobCurator, times(1)).deleteJobs(captor.capture());
        }
        else {
            verify(this.jobManager, times(1)).cleanupJobs(captor.capture());
            verify(this.jobCurator, never()).deleteJobs(any(AsyncJobStatusQueryBuilder.class));
        }

        AsyncJobStatusQueryBuilder builder = captor.getValue();
        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getOwnerIds());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertEquals(value, builder.getStartDate());
        assertNull(builder.getEndDate());

        // Verify the output passthrough is working properly
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @MethodSource("targetJobStatusesByDatesProvider")
    public void testCleanupTerminalJobsByEndDate(Date value) {
        this.testCleanupTerminalJobsByEndDate(value, false);
        clearInvocations(this.jobManager, this.jobCurator);
        this.testCleanupTerminalJobsByEndDate(value, true);
    }

    public void testCleanupTerminalJobsByEndDate(Date value, boolean force) {
        int expected = new Random().nextInt();
        doReturn(expected).when(this.jobManager).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
        doReturn(expected).when(this.jobCurator).deleteJobs(any(AsyncJobStatusQueryBuilder.class));

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobResource resource = this.buildJobResource();
        int result = resource.cleanupTerminalJobs(null, null, null, null, null,
            null, null, null, value, force);

        // Verify the input passthrough is working properly
        if (force) {
            verify(this.jobManager, never()).cleanupJobs(any(AsyncJobStatusQueryBuilder.class));
            verify(this.jobCurator, times(1)).deleteJobs(captor.capture());
        }
        else {
            verify(this.jobManager, times(1)).cleanupJobs(captor.capture());
            verify(this.jobCurator, never()).deleteJobs(any(AsyncJobStatusQueryBuilder.class));
        }

        AsyncJobStatusQueryBuilder builder = captor.getValue();
        assertNotNull(builder);
        assertNull(builder.getJobIds());
        assertNull(builder.getJobKeys());
        assertNull(builder.getOwnerIds());
        assertNull(builder.getPrincipalNames());
        assertNull(builder.getOrigins());
        assertNull(builder.getExecutors());
        assertNull(builder.getStartDate());
        assertEquals(value, builder.getEndDate());

        // Verify the output passthrough is working properly
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "false" })
    public void testCleanupTerminalJobsFailsOnInvalidDateRange(boolean force) {
        Date start = Util.addDaysToDt(-2);
        Date end = Util.addDaysToDt(2);

        JobResource resource = this.buildJobResource();
        assertThrows(BadRequestException.class, () ->
            resource.cleanupTerminalJobs(null, null, null, null, null, null, null, end, start, force));
    }

    private void setTriggerableJobs(String... jobs) {
        String value = String.join(",", jobs);
        this.config.setProperty(ConfigProperties.ASYNC_JOBS_TRIGGERABLE_JOBS, value);
    }

    @ParameterizedTest
    @ValueSource(strings = { "job1", "job2", "job3" })
    public void testScheduleJob(String jobKey) throws JobException {
        this.setTriggerableJobs("job1", "job2", "job3");

        ArgumentCaptor<JobConfig> captor = ArgumentCaptor.forClass(JobConfig.class);

        AsyncJobStatus status = mock(AsyncJobStatus.class);

        doReturn(jobKey).when(status).getJobKey();
        doReturn(status).when(this.jobManager).queueJob(any(JobConfig.class));

        JobResource resource = this.buildJobResource();
        AsyncJobStatusDTO result = resource.scheduleJob(jobKey);

        assertNotNull(result);
        assertEquals(jobKey, result.getKey());

        verify(this.jobManager, times(1)).queueJob(captor.capture());

        JobConfig config = captor.getValue();
        assertNotNull(config);
        assertEquals(jobKey, config.getJobKey());
    }

    @Test
    public void testScheduleJobFailsIfNotOnTriggerableConfig() {
        this.setTriggerableJobs("job1", "job2", "job3");

        JobResource resource = this.buildJobResource();
        assertThrows(ForbiddenException.class, () -> resource.scheduleJob("bad_job_key"));
    }

    @ParameterizedTest
    @MethodSource("emptyInputProvider")
    public void testScheduleJobFailsOnEmptyInput(String jobKey) {
        this.setTriggerableJobs("job1", "job2", "job3");

        JobResource resource = this.buildJobResource();
        assertThrows(BadRequestException.class, () -> resource.scheduleJob(jobKey));
    }
}
