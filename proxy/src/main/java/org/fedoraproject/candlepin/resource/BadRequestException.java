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
package org.fedoraproject.candlepin.resource;

import com.sun.jersey.api.client.ClientResponse.Status;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * Represents a BAD_REQUEST (HTTP 400) error.
 */
public class BadRequestException extends WebApplicationException {
    /**
     * 
     */
    private static final long serialVersionUID = -3430329252623764984L;

    /**
     * default ctor
     * @param message Exception message
     */
    public BadRequestException(String message) {
        super(Response.status(Status.BAD_REQUEST)
                .entity(message)
                .type("text/plain")
                .build()
        );
    }
}
