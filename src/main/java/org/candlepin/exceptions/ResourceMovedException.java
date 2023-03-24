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
package org.candlepin.exceptions;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response.Status;

/**
 * Represents a See Other (HTTP 303) error.
 */
public class ResourceMovedException extends CandlepinException {
    String location = null;

    public ResourceMovedException(String argLocation) {
        super(Status.SEE_OTHER, "The resource has moved to the location: " + argLocation);
        location = argLocation;
    }

    @Override
    public Map<String, String> headers() {
        HashMap<String, String> negHeaders = new HashMap<>();
        negHeaders.put("Location", location);
        return  negHeaders;
    }
}
