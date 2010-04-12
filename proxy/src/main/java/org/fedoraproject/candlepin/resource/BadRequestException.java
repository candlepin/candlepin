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


/**
 * Represents a BAD_REQUEST (HTTP 400) error.
 */
public class BadRequestException extends RuntimeException {
    private static final long serialVersionUID = -3430329252623764984L;
    
    private final ExceptionMessage message;

    /**
     * default ctor
     */
    public BadRequestException(String shortMessage, String longMessage) {
        message = new ExceptionMessage()
            .setLabel(shortMessage)
            .setDisplayMessage(longMessage);
    }
    
    public ExceptionMessage message() {
        return message;
    }
}
