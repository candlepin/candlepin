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
package org.candlepin.policy.js.compliance;

import com.fasterxml.jackson.annotation.JsonFilter;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlTransient;

/**
 * ComplianceReason
 */
@JsonFilter("ComplianceReasonFilter")
public class ComplianceReason {
    /** Commonly used/recognized attributes */
    public static final class Attributes {
        /** Attribute for specifying an entitlement ID which is not covered */
        public static final String ENTITLEMENT_ID = "entitlement_id";

        /** Attribute for specifying the marketing name of an entitlement stack which is not covered */
        public static final String MARKETING_NAME = "name";

        /** Attribute used to specify a product which is not covered */
        public static final String PRODUCT_ID = "product_id";

        /** Attribute used to identify stacked products and pools */
        public static final String STACKING_ID = "stack_id";

        /** Attribute used for specifying the property of an entitlement that is covered */
        public static final String COVERED = "covered";

        /** Attribute used for specifying the property of a system or product which is not covered by an
         *  entitlement */
        public static final String PRESENT = "has";
    }

    /** Commonly used keys for compliance failure reasons */
    public static final class ReasonKeys {
        /** Key for specifying the system is not covered */
        public static final String NOT_COVERED = "NOTCOVERED";

        /** TODO: Fill this in */
        public static final String ARCHITECTURE = "ARCH";

        /** TODO: Fill this in and update the const name */
        public static final String SOCKETS = "SOCKETS";

        /** TODO: Fill this in and update the const name */
        public static final String CORES = "CORES";

        /** TODO: Fill this in and update the const name */
        public static final String RAM = "RAM";

        /** TODO: Fill this in */
        public static final String VIRT_LIMIT = "GUEST_LIMIT";

        /** TODO: Fill this in */
        public static final String VIRT_CPU = "VCPU";

        /** TODO: Fill this in */
        public static final String UNMAPPED_GUEST = "UNMAPPEDGUEST";

        /** TODO: Fill this in */
        public static final String STORAGE_BAND = "STORAGE_BAND";
    }

    private String key;
    private String message;
    private Map<String, String> attributes;

    public ComplianceReason() {
        this.attributes = new HashMap<>();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    @XmlTransient
    public boolean isStacked() {
        return attributes.containsKey(Attributes.STACKING_ID);
    }

    @XmlTransient
    public boolean isNonCovered() {
        return attributes.containsKey(Attributes.PRODUCT_ID);
    }
}
