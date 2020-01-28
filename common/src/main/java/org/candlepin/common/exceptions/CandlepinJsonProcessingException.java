/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;



/**
 * Thrown when an exception occurs while deserializing or processing JSON.
 */
public class CandlepinJsonProcessingException extends JsonProcessingException {

    /**
     * Creates a new CandlepinJsonProcessingException instance with the given exception message.
     *
     * @param message
     *  The exception message
     */
    public CandlepinJsonProcessingException(String message) {
        super(message);
    }

    /**
     * Creates a new CandlepinJsonProcessingException instance with the given exception message and
     * location.
     *
     * @param message
     *  The exception message
     *
     * @param location
     *  The location where the exception occurred
     */
    public CandlepinJsonProcessingException(String message, JsonLocation location) {
        super(message, location);
    }
}
