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

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response.Status;


/**
 * Represents an Unauthorized (HTTP 401) error.
 */
public class NotAuthorizedException extends CandlepinException {
    public NotAuthorizedException(String message) {
        super(Status.UNAUTHORIZED, message);
    }

    public NotAuthorizedException(String message, boolean logException) {
        super(Status.UNAUTHORIZED, message, logException);
    }

    @Override
    public Map<String, String> headers() {
        HashMap<String, String> negHeaders = new HashMap<String, String>();
        negHeaders.put("WWW-Authenticate", "Negotiate");
        negHeaders.put("WWW-Authenticate", "Basic Realm=candlepin");
        return  negHeaders;
    }
}
