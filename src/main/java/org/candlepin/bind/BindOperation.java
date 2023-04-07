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
package org.candlepin.bind;

/**
 * Defines the workflow for the binding chain of responsibility
 */
public interface BindOperation {

    /* Pre lock step. In this step, none of the entities except the
     * consumer is locked. The intent is to gather as much as information
     * as we can before we lock the respective entities.
     * Returns true if we can continue processing, false otherwise.
     */
    boolean preProcess(BindContext context);

    /* Post lock step. Usually in this step, we simply persist the pre-calculated
     * creates or updates from the pre=process step.
     * Returns true if we can continue processing, false otherwise.
     */
    boolean execute(BindContext context);
}
