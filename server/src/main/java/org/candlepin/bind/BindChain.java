/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.bind;

import java.util.ArrayList;
import java.util.List;

/** Holds and represents the binding chain of responsibility.  Inspired by the servlet filter interfaces. */
public class BindChain {
    private List<BindOperation> operations;
    private int preProcessIndex = -1;
    private int lockIndex = -1;
    private int executeIndex = -1;

    public BindChain() {
        operations = new ArrayList<BindOperation>();
    }

    public void preProcess(BindContext context) {
        preProcessIndex++;
        if (preProcessIndex < operations.size()) {
            operations.get(preProcessIndex).preProcess(context, this);
        }
    }

    public void acquireLock(BindContext context) {
        lockIndex++;
        if (lockIndex < operations.size()) {
            operations.get(lockIndex).acquireLock(context, this);
        }
    }

    public void execute(BindContext context) {
        executeIndex++;

        if (executeIndex < operations.size()) {
            operations.get(executeIndex).execute(context, this);
        }
    }

    public void addOperation(BindOperation operation) {
        operations.add(operation);
    }

    // TODO Implement other assorted list methods if desired.
}
