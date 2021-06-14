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

import org.candlepin.common.logging.LoggingUtil;

import org.slf4j.MDC;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * ExceptionMessage
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class ExceptionMessage implements Serializable {

    private static final long serialVersionUID = 6758808334936389511L;

    private String displayMessage;
    private String requestUuid;

    public ExceptionMessage() {
        this.setRequestUuid();
    }

    public ExceptionMessage(String displayMessage) {
        this.displayMessage = displayMessage;
        this.setRequestUuid();
    }

    public ExceptionMessage setDisplayMessage(String displayMessage) {
        this.displayMessage = displayMessage;
        return this;
    }

    public String getDisplayMessage() {
        return displayMessage;
    }

    public String getRequestUuid() {
        return requestUuid;
    }

    public void setRequestUuid(String requestUuid) {
        this.requestUuid = requestUuid;
    }

    /**
     * Pulls the request UUID from the log4j MDC if possible, and sets them for return to the client.
     *
     * Doesn't include the requestType, as I believe we can assume it's an HTTP request
     * and not a job, if this exception is being used.
     */
    private void setRequestUuid() {
        if (MDC.get(LoggingUtil.MDC_REQUEST_UUID_KEY) != null) {
            this.requestUuid = (String) MDC.get(LoggingUtil.MDC_REQUEST_UUID_KEY);
        }
    }

    public String toString() {
        // This needs to always return a string. If we don't have a display message, we should return
        // an empty string instead.
        String displayMessage = this.getDisplayMessage();
        return displayMessage != null ? displayMessage : "";
    }
}
