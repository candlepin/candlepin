/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
 * TooManyRequestsException represents an exception that generates a response with a HTTP 429 status code.
 */
public class TooManyRequestsException extends CandlepinException {
    private Integer retryAfter;

    /**
     * Creates a new too many requests exception with the provided message.
     *
     * @param message
     *  the message used to create a new too many requests exception
     */
    public TooManyRequestsException(String message) {
        super(Status.TOO_MANY_REQUESTS, message);
    }

    /**
     * Creates a new too many requests exception with the provided message and provided throwable cause.
     *
     * @param message
     *  the message used to create a new too many requests exception
     *
     * @param cause
     * the throwable cause used to create a new too many requests exception
     */
    public TooManyRequestsException(String message, Throwable cause) {
        super(Status.TOO_MANY_REQUESTS, message, cause);
    }

    /**
     * Sets the retry after value used to populate the 'Retry-After' header. If a null value is provided, the
     * 'Retry-After' header will not be populated in the response. Subsequent calls will overwrite the
     * previously set value.
     *
     * @param retryAfter
     *  the value to set for the 'Retry-After' header
     *
     * @return this too many requests exception instance
     */
    public TooManyRequestsException setRetryAfter(Integer retryAfter) {
        this.retryAfter = retryAfter;
        return this;
    }

    /**
     * @return the value that will be set for the 'Retry-After' header and a null value indicates that the
     *  header will not be set
     */
    public Integer getRetryAfter() {
        return this.retryAfter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> headers() {
        HashMap<String, String> headers = new HashMap<>();
        if (this.retryAfter == null) {
            return headers;
        }

        headers.put("Retry-After", String.valueOf(this.retryAfter));

        return  headers;
    }

}
