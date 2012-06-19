/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.exceptions;

import javax.ws.rs.core.Response.Status;

/**
 * Thrown when there is a resource conflict such as an out of date edit.
 */
public class ConflictException extends CandlepinException {

    /**
     * @param message
     */
    public ConflictException(String message) {
        super(Status.CONFLICT, message);
    }

    /**
     * @param message
     * @param e
     */
    public ConflictException(String message, Throwable e) {
        super(Status.CONFLICT, message, e);
    }

}
