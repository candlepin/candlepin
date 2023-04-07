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
package org.candlepin.resource.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.resource.util.JobStateMapper.ExternalJobState;
import org.candlepin.util.Util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;



/**
 * Test suite for the JobStateMapper class
 */
public class JobStateMapperTest {

    public static Stream<Arguments> internalToExternalTestProvider() {
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
    @MethodSource("internalToExternalTestProvider")
    public void testTranslateInternalStateToExternalState(JobState input, ExternalJobState expected) {
        ExternalJobState output = JobStateMapper.translateState(input);
        assertEquals(expected, output);
    }

    public static Stream<Arguments> externalToInternalTestProvider() {
        return Stream.of(
            Arguments.of(null, null),

            Arguments.of(ExternalJobState.CREATED,
                Util.asSet(JobState.CREATED, JobState.WAITING, JobState.SCHEDULED, JobState.QUEUED)),

            Arguments.of(ExternalJobState.RUNNING, Util.asSet(JobState.RUNNING, JobState.FAILED_WITH_RETRY)),

            Arguments.of(ExternalJobState.FINISHED, Util.asSet(JobState.FINISHED)),

            Arguments.of(ExternalJobState.FAILED, Util.asSet(JobState.FAILED, JobState.ABORTED)),

            Arguments.of(ExternalJobState.CANCELED, Util.asSet(JobState.CANCELED))
        );
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {1}")
    @MethodSource("externalToInternalTestProvider")
    public void testTranslateExternalStateToInternalState(ExternalJobState input, Set<JobState> expected) {
        Set<JobState> output = JobStateMapper.translateState(input);
        assertEquals(expected, output);
    }

    public static Stream<Arguments> bulkExternalToInternalTestProvider() {
        Map<ExternalJobState, Set<JobState>> statemap = new HashMap<>();

        statemap.put(ExternalJobState.CREATED,
            Util.asSet(JobState.CREATED, JobState.WAITING, JobState.SCHEDULED, JobState.QUEUED));
        statemap.put(ExternalJobState.RUNNING, Util.asSet(JobState.RUNNING, JobState.FAILED_WITH_RETRY));
        statemap.put(ExternalJobState.FINISHED, Util.asSet(JobState.FINISHED));
        statemap.put(ExternalJobState.FAILED, Util.asSet(JobState.FAILED, JobState.ABORTED));
        statemap.put(ExternalJobState.CANCELED, Util.asSet(JobState.CANCELED));

        List<Arguments> arguments = new LinkedList<>();

        arguments.add(Arguments.of(null, null));
        arguments.add(Arguments.of(Collections.emptyList(), Collections.emptySet()));

        // Make blocks of arguments in the following sizes
        ExternalJobState[] states = ExternalJobState.values();
        for (int blockSize = 1; blockSize <= 3; ++blockSize) {
            for (int i = 0; i + blockSize <= states.length; ++i) {
                List<ExternalJobState> input = new LinkedList<>();
                Set<JobState> expected = new HashSet<>();

                for (int offset = 0; offset < blockSize; ++offset) {
                    ExternalJobState state = states[i + offset];

                    input.add(state);
                    expected.addAll(statemap.get(state));
                }

                arguments.add(Arguments.of(input, expected));
            }
        }

        return arguments.stream();
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {1}")
    @MethodSource("bulkExternalToInternalTestProvider")
    public void testTranslateExternalStatesToInternalStates(List<ExternalJobState> input,
        Set<JobState> expected) {

        Set<JobState> output = JobStateMapper.translateStates(input);
        assertEquals(expected, output);
    }

}
