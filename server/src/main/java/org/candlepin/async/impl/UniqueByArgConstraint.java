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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;



/**
 * The UniqueByArgConstraint constrains queuing of a job if another job with same same key and
 * value of a given parameter, or set of parameters, already exists in a non-terminal state.
 */
public class UniqueByArgConstraint implements JobConstraint {

    private final List<String> params;

    /**
     * Creates a new UniqueByArgConstraint using the specified parameters as the target. If multiple
     * parameters are provided, they are checked in the order provided.
     *
     * @param params
     *  The parameter, or parameters, to target with this constraint
     *
     * @throws IllegalArgumentException
     *  if params is null or empty, or contains a parameter which is null or empty
     */
    public UniqueByArgConstraint(String... params) {
        this(params != null ? Arrays.asList(params) : null);
    }

    /**
     * Creates a new UniqueByArgConstraint using the specified parameters as the target. If multiple
     * parameters are provided, they are checked in the order provided.
     *
     * @param params
     *  A list of parameters to target with this constraint
     *
     * @throws IllegalArgumentException
     *  if params is null or empty, or contains a parameter which is null or empty
     */
    public UniqueByArgConstraint(List<String> params) {
        if (params == null || params.isEmpty()) {
            throw new IllegalArgumentException("params is null or empty");
        }

        List<String> plist = new ArrayList<>(params.size());
        for (String param : params) {
            if (param == null || param.isEmpty()) {
                throw new IllegalArgumentException("params contains a null or empty parmaeter");
            }

            plist.add(param);
        }

        this.params = Collections.<String>unmodifiableList(plist);
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
            boolean matched = true;

            for (String param : this.params) {
                if (iArgs.containsKey(param) && eArgs.containsKey(param)) {
                    String iValue = iArgs.getSerializedValue(param);
                    String eValue = eArgs.getSerializedValue(param);

                    if (!(iValue != null ? iValue.equals(eValue) : eValue == null)) {
                        matched = false;
                        break;
                    }
                }
                else {
                    matched = false;
                    break;
                }
            }

            return matched;
        }

        return false;
    }
}
