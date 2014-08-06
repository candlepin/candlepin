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
package org.candlepin.audit;

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.policy.js.compliance.ComplianceStatus;

import java.util.Set;

/**
 * ComplianceEventData encapsulates all of the data we want to send on a compliance
 * event, which alows us to avoid modifying Event unnecessarily.
 */
public class ComplianceEventData {

    private Consumer consumer;
    private Set<Entitlement> entitlements;
    private ComplianceStatus status;

    public ComplianceEventData(Consumer consumer, Set<Entitlement> entitlements, ComplianceStatus status) {
        this.consumer = consumer;
        this.entitlements = entitlements;
        this.status = status;
    }

    /**
     * @return the consumer
     */
    public Consumer getConsumer() {
        return consumer;
    }

    /**
     * @param consumer the consumer to set
     */
    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    /**
     * @return the entitlements
     */
    public Set<Entitlement> getEntitlements() {
        return entitlements;
    }

    /**
     * @param entitlements the entitlements to set
     */
    public void setEntitlements(Set<Entitlement> entitlements) {
        this.entitlements = entitlements;
    }

    /**
     * @return the status
     */
    public ComplianceStatus getStatus() {
        return status;
    }

    /**
     * @param status the status to set
     */
    public void setStatus(ComplianceStatus status) {
        this.status = status;
    }

}
