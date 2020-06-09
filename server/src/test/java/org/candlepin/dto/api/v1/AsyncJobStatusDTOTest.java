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

import org.candlepin.dto.AbstractDTOTest;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;



/**
 * Test suite for the AsyncJobStatusDTO class
 */
public class AsyncJobStatusDTOTest extends AbstractDTOTest<AsyncJobStatusDTO> {

    protected Map<String, Object> values;

    public AsyncJobStatusDTOTest() {
        super(AsyncJobStatusDTO.class);

        this.values = new HashMap<>();
        this.values.put("Id", "test_job_id");
        this.values.put("JobKey", "job_key");
        this.values.put("Name", "job_name");
        this.values.put("Group", "job_group");
        this.values.put("Origin", "localhost-origin");
        this.values.put("Executor", "localhost-executor");
        this.values.put("Principal", "admin");
        this.values.put("State", "some_state");
        this.values.put("PreviousState", "prev_state");
        this.values.put("StartTime", new Date());
        this.values.put("EndTime", new Date());
        this.values.put("Attempts", 3);
        this.values.put("MaxAttempts", 10);
        this.values.put("Result", "job_result");

        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getInputValueForMutator(String field) {
        return this.values.get(field);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getOutputValueForAccessor(String field, Object input) {
        // Nothing to do here
        return input;
    }
}
