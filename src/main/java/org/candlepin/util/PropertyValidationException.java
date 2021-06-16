/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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
package org.candlepin.util;

import org.candlepin.exceptions.CandlepinException;

import javax.ws.rs.core.Response.Status;



/**
 * Represents an exception caused by a property (attribute, fact, etc.) containing an illegal
 * key or value.
 */
public class PropertyValidationException extends CandlepinException {
    private static final long serialVersionUID = 1L;

    public PropertyValidationException(String message) {
        super(Status.BAD_REQUEST, message);
    }
}
