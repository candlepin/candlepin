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

package org.candlepin.exceptions;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response;

public class TooManyRequestsException extends CandlepinException {
    private final int retryAfterTime;

    /**
     * @param time
     *      Time in seconds for Retry-After header
     */
    public TooManyRequestsException(int time) {
        super(Response.Status.TOO_MANY_REQUESTS, "Too many requests. Retry after: " + time + " seconds.");
        this.retryAfterTime = time;
    }

    @Override
    public Map<String, String> headers() {
        HashMap<String, String> negHeaders = new HashMap<>();
        negHeaders.put("Retry-After", String.valueOf(this.retryAfterTime));
        return  negHeaders;
    }

    public int getRetryAfterTime() {
        return this.retryAfterTime;
    }
}
