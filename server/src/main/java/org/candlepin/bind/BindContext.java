/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.bind;

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;

import java.util.Map;

/**
 * Container class for holding binding information
 */
public class BindContext {

    private Consumer consumer;
    private Map<Object, Entitlement> entitlements;

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    public Map<Object, Entitlement> getEntitlements() {
        return entitlements;
    }

    public void setEntitlements(Map<Object, Entitlement> entitlements) {
        this.entitlements = entitlements;
    }
}
