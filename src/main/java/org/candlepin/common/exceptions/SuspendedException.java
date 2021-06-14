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
package org.candlepin.common.exceptions;

import javax.ws.rs.core.Response.Status;


/**
 * Thrown when Candlepin is unavailable, possibly due to Suspend Mode.
 */
public class SuspendedException extends CandlepinException {
    public SuspendedException(String message) {
        super(Status.SERVICE_UNAVAILABLE, message);
    }

    /**
     * @param message
     * @param e
     */
    public SuspendedException(String message, Throwable e) {
        super(Status.SERVICE_UNAVAILABLE, message, e);
    }


}
