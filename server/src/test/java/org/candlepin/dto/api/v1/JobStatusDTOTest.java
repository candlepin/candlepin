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

import org.bouncycastle.util.Strings;
import org.candlepin.dto.AbstractDTOTest;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Test suite for the JobStatusDTO class
 */
public class JobStatusDTOTest extends AbstractDTOTest<JobStatusDTO> {

    protected Map<String, Object> values;

    public JobStatusDTOTest() {
        super(JobStatusDTO.class);

        Object bytes = Strings.toByteArray("random byte array");
        this.values = new HashMap<String, Object>();
        this.values.put("Id", "test-id");
        this.values.put("Group", "test-job-group");
        this.values.put("State", "test-state");
        this.values.put("StartTime", new Date());
        this.values.put("FinishTime", new Date());
        this.values.put("Result", "test-result");
        this.values.put("PrincipalName", "test-principal-name");
        this.values.put("TargetType", "test-target-type");
        this.values.put("TargetId", "test-target-id");
        this.values.put("OwnerId", "test-owner-id");
        this.values.put("CorrelationId", "test-correlation-id");
        this.values.put("ResultData", bytes);
        this.values.put("Done", true);
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
