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

    private String key;
    private String message;
    private Map<String, String> attributes;

    public ComplianceReason() {
        this.attributes = new HashMap<String, String>();
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
        return attributes.containsKey("stack_id");
    }

    @XmlTransient
    public boolean isNonCovered() {
        return attributes.containsKey("product_id");
    }
}
