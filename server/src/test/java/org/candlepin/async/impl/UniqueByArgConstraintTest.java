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
package org.candlepin.async.impl;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConstraint;
import org.candlepin.model.AsyncJobStatus;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;



public class UniqueByArgConstraintTest {

    private Map<String, Object> map(String... pairs) {
        Map<String, Object> map = new HashMap<>();

        if (pairs != null) {
            for (int i = 0; i < pairs.length; i += 2) {
                map.put(pairs[i], pairs[i + 1]);
            }
        }

        return map;
    }

    private AsyncJobStatus buildJobStatus(String name, String jobKey, Map<String, Object> params) {
        AsyncJobStatus status = new AsyncJobStatus();
        status.setName(name);
        status.setJobKey(jobKey);

        if (params != null) {
            JobConfig config = JobConfig.forJob("dummy_job");

            for (Map.Entry<String, Object> entry : params.entrySet()) {
                config.setJobArgument(entry.getKey(), entry.getValue());
            }

            status.setJobArguments(config.getJobArguments());
        }

        return status;
    }

    @Test
    public void testStandardMatching() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map("param1", "val1"));
        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map("param1", "val1"));

        JobConstraint constraint = new UniqueByArgConstraint("param1");
        boolean result = constraint.test(inbound, existing);

        assertTrue(result);
    }

    @Test
    public void testStandardMatchingFromCollection() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map("param1", "val1"));
        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map("param1", "val1"));

        JobConstraint constraint = new UniqueByArgConstraint(Arrays.asList("param1"));
        boolean result = constraint.test(inbound, existing);

        assertTrue(result);
    }

    @Test
    public void testMatchingOnJobsWithMultipleParams() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map(
            "paramA", "valA",
            "param2", "val2",
            "paramC", "valC"));

        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map(
            "param1", "val1",
            "param2", "val2",
            "param3", "val3"));

        JobConstraint constraint = new UniqueByArgConstraint("param2");
        boolean result = constraint.test(inbound, existing);

        assertTrue(result);
    }

    @Test
    public void testMatchingOnJobsWithMultipleParamsFromCollection() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map(
            "paramA", "valA",
            "param2", "val2",
            "paramC", "valC"));

        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map(
            "param1", "val1",
            "param2", "val2",
            "param3", "val3"));

        JobConstraint constraint = new UniqueByArgConstraint(Arrays.asList("param2"));
        boolean result = constraint.test(inbound, existing);

        assertTrue(result);
    }

    @Test
    public void testMultiMatchingOnJobsWithMultipleParams() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map(
            "param1", "val1",
            "param2", "val2",
            "param3", "val3"));

        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map(
            "param1", "val1",
            "param2", "val2",
            "param3", "val3"));

        JobConstraint constraint = new UniqueByArgConstraint("param1", "param2", "param3");
        boolean result = constraint.test(inbound, existing);

        assertTrue(result);
    }

    @Test
    public void testMultiMatchingOnJobsWithMultipleParamsFromCollection() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map(
            "param1", "val1",
            "param2", "val2",
            "param3", "val3"));

        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map(
            "param1", "val1",
            "param2", "val2",
            "param3", "val3"));

        JobConstraint constraint = new UniqueByArgConstraint(Arrays.asList("param1", "param2", "param3"));
        boolean result = constraint.test(inbound, existing);

        assertTrue(result);
    }

    @Test
    public void testMultiMatchingOnJobsWithMultipleParamsOutOfOrder() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map(
            "param1", "val1",
            "param2", "val2",
            "param3", "val3"));

        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map(
            "param1", "val1",
            "param2", "val2",
            "param3", "val3"));

        JobConstraint constraint = new UniqueByArgConstraint("param2", "param1", "param3");
        boolean result = constraint.test(inbound, existing);

        assertTrue(result);
    }

    @Test
    public void testMultiMatchingOnJobsWithMultipleParamsOutOfOrderFromCollection() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map(
            "param1", "val1",
            "param2", "val2",
            "param3", "val3"));

        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map(
            "param1", "val1",
            "param2", "val2",
            "param3", "val3"));

        JobConstraint constraint = new UniqueByArgConstraint(Arrays.asList("param2", "param1", "param3"));
        boolean result = constraint.test(inbound, existing);

        assertTrue(result);
    }

    @Test
    public void testMultiMatchingOnJobsWithMultipleParamsAndExtraParams() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map(
            "paramA", "valA",
            "param1", "val1",
            "paramB", "valB",
            "param2", "val2",
            "paramC", "valC",
            "param3", "val3",
            "paramD", "valD"));

        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map(
            "paramR", "valR",
            "param1", "val1",
            "paramS", "valS",
            "param2", "val2",
            "paramT", "valT",
            "param3", "val3",
            "paramU", "valU"));

        JobConstraint constraint = new UniqueByArgConstraint("param2", "param1", "param3");
        boolean result = constraint.test(inbound, existing);

        assertTrue(result);
    }

    @Test
    public void testNoMatchOnKeyMismatch() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map("param1", "val1"));
        AsyncJobStatus existing = this.buildJobStatus("existing", "alt_key", this.map("param1", "val1"));

        JobConstraint constraint = new UniqueByArgConstraint("param1");
        boolean result = constraint.test(inbound, existing);

        assertFalse(result);
    }

    @Test
    public void testNoMatchOnKeyMismatchWithMultiParam() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map(
            "param1", "val1",
            "param2", "val2",
            "param3", "val3"));

        AsyncJobStatus existing = this.buildJobStatus("existing", "alt_key", this.map(
            "param1", "val1",
            "param2", "val2",
            "param3", "val3"));

        JobConstraint constraint = new UniqueByArgConstraint("param1", "param2", "param3");
        boolean result = constraint.test(inbound, existing);

        assertFalse(result);
    }

    @Test
    public void testNoMatchOnParamMismatch() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map("param1", "val1"));
        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map("paramX", "val1"));

        JobConstraint constraint = new UniqueByArgConstraint("param1");
        boolean result = constraint.test(inbound, existing);

        assertFalse(result);
    }

    @Test
    public void testMultiParamsNoMatchOnParamMismatch() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map(
            "param1", "val1",
            "param2", "val2",
            "param3", "val3"));

        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map(
            "param1", "val1",
            "paramX", "val2",
            "param3", "val3"));

        JobConstraint constraint = new UniqueByArgConstraint("param1", "param2", "param3");
        boolean result = constraint.test(inbound, existing);

        assertFalse(result);
    }

    @Test
    public void testNoMatchOnArgMismatch() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map("param1", "val1"));
        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map("param1", "valX"));

        JobConstraint constraint = new UniqueByArgConstraint("param1");
        boolean result = constraint.test(inbound, existing);

        assertFalse(result);
    }

    @Test
    public void testMultiParamsNoMatchOnArgMismatch() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map(
            "param1", "val1",
            "param2", "val2",
            "param3", "val3"));

        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map(
            "param1", "val1",
            "param2", "valX",
            "param3", "val3"));

        JobConstraint constraint = new UniqueByArgConstraint("param1", "param2", "param3");
        boolean result = constraint.test(inbound, existing);

        assertFalse(result);
    }

    @Test
    public void testNoMatchWhenInboundJobLacksParam() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map("param2", "val2"));
        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map("param1", null));

        JobConstraint constraint = new UniqueByArgConstraint("param1");
        boolean result = constraint.test(inbound, existing);

        assertFalse(result);
    }

    @Test
    public void testMultiParamNoMatchWhenInboundJobLacksParam() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map(
            "param1", "val1",
            "param2", "val2"));

        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map(
            "param1", "val1",
            "param2", "val2",
            "param3", "val3"));

        JobConstraint constraint = new UniqueByArgConstraint("param1", "param2", "param3");
        boolean result = constraint.test(inbound, existing);

        assertFalse(result);
    }

    @Test
    public void testNoMatchWhenExistingJobsLackParam() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map("param1", null));
        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map("param2", "val2"));

        JobConstraint constraint = new UniqueByArgConstraint("param1");
        boolean result = constraint.test(inbound, existing);

        assertFalse(result);
    }

    @Test
    public void testMultiParamNoMatchWhenExistingJobsLackParam() {
        AsyncJobStatus inbound = this.buildJobStatus("inbound", "test_key", this.map(
            "param1", "val1",
            "param2", "val2",
            "param3", "val3"));

        AsyncJobStatus existing = this.buildJobStatus("existing", "test_key", this.map(
            "param1", "val1",
            "param3", "val3"));

        JobConstraint constraint = new UniqueByArgConstraint("param1", "param2", "param3");
        boolean result = constraint.test(inbound, existing);

        assertFalse(result);
    }

}
