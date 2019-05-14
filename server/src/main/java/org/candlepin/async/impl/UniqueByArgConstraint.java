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

import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConstraint;
import org.candlepin.model.AsyncJobStatus;



/**
 * The UniqueByArgConstraint constrains queuing of a job if another job with same same key
 * and value of a given parameter already exists in a non-terminal state.
 */
public class UniqueByArgConstraint implements JobConstraint {

    private final String param;

    /**
     * Creates a new UniqueByArgConstraint using the specified parameter as the target.
     *
     * @param param
     *  The parameter to target with this constraint
     *
     * @throws IllegalArgumentException
     *  if param is null or empty
     */
    public UniqueByArgConstraint(String param) {
        if (param == null || param.isEmpty()) {
            throw new IllegalArgumentException("param is null or empty");
        }

        this.param = param;
    }

    /**
     * @{inheritDoc}
     */
    @Override
    public boolean test(AsyncJobStatus inbound, AsyncJobStatus existing) {
        if (inbound == null) {
            throw new IllegalArgumentException("inbound is null");
        }

        if (existing == null) {
            throw new IllegalArgumentException("existing is null");
        }

        String iJobKey = inbound.getJobKey();
        JobArguments iArgs = inbound.getJobArguments();

        String eJobKey = existing.getJobKey();
        JobArguments eArgs = existing.getJobArguments();

        if (iJobKey != null ? iJobKey.equals(eJobKey) : eJobKey == null) {
            if (iArgs.containsKey(this.param) && eArgs.containsKey(this.param)) {
                String iValue = iArgs.getSerializedValue(this.param);
                String eValue = eArgs.getSerializedValue(this.param);

                return iValue != null ? iValue.equals(eValue) : eValue == null;
            }
        }

        return false;
    }
}
