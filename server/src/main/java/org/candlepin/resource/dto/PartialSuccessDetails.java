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
package org.candlepin.resource.dto;

import org.candlepin.model.Consumer;

import java.io.Serializable;

/**
 * Async HypervisorUpdateJob can finish with three distinct outcomes:
 *  - Success, the checkin went through without any error
 *  - Error, the checkin was unsuccessfull updating/creating any Hypervisor
 *  - Partial Success, the checkin successfully created/updated some Hypervisors but
 *    the Checkin failed at some point because of a particular Consumer (either
 *    Hypervisor or Guest)
 *
 * In case of Partial Success, we populate the HypervisorUpdateResult field
 * partialSuccessDetails with an instance of this class.
 *
 * @author fnguyen
 *
 */
public class PartialSuccessDetails implements Serializable {
    private Consumer failedConsumer;
    private String displayMessage;
    private Exception exception;

    public PartialSuccessDetails(Consumer failedGuest, String displayMessage, Exception exception) {
        super();
        this.failedConsumer = failedGuest;
        this.displayMessage = displayMessage;
        this.exception = exception;
    }

    public Consumer getFailedConsumer() {
        return failedConsumer;
    }

    public String getDisplayMessage() {
        return displayMessage;
    }

    public Exception getException() {
        return exception;
    }
}
