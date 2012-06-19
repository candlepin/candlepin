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
 * Represents a BAD_REQUEST (HTTP 400) error.
 */
public class BadRequestException extends CandlepinException {
    /**
     *
     */
    private static final long serialVersionUID = 6927030276240437718L;

    public BadRequestException(String message) {
        super(Status.BAD_REQUEST, message);
    }

    public BadRequestException(String message, Throwable t) {
        super(Status.BAD_REQUEST, message, t);
    }
}
