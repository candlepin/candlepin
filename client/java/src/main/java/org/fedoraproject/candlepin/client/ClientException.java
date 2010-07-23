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
package org.fedoraproject.candlepin.client;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * ClientException
 */
public class ClientException extends RuntimeException {

    private static final long serialVersionUID = -7217728552039510992L;

    private Response.Status status;

    public ClientException() {
        super();
    }

    public ClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public ClientException(String message) {
        super(message);
    }

    public ClientException(Throwable cause) {
        super(cause);
    }

    public ClientException(Status status) {
        this.status = status;
    }

    public ClientException(Status status, String message) {
        this(message);
        this.status = status;
    }

    public Response.Status getStatus() {
        return status;
    }

}
