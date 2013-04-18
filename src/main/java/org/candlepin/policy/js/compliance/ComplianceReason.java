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

import java.util.HashMap;
import java.util.Map;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.xnap.commons.i18n.I18n;
import org.candlepin.model.ConsumerInstalledProduct;
import com.google.inject.Inject;

/**
 * ComplianceReason
 */
public class ComplianceReason {

    private String key;
    private String message;
    private Map<String, String> attributes;
    private transient Consumer consumer; //used to get data for strings
    private transient Map<String, String> keys;

    @Inject private transient I18n i18n;

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
        String transKey = keys.keySet().contains(key) ? key : "DEFAULT";
        String id, marketingName;

        if (attributes.keySet().contains("entitlement_id")) {
            id = attributes.get("entitlement_id");
            marketingName = getMarketingName(id);
            return i18n.tr(keys.get(transKey), marketingName,
                attributes.get("covered"), attributes.get("has"), key);
        }
        else if (attributes.keySet().contains("stack_id")) {
            id = attributes.get("stack_id");
            marketingName = getStackedMarketingName(id);
            return i18n.tr(keys.get(transKey), marketingName,
                attributes.get("covered"), attributes.get("has"), key);
        }
        else {
            id = attributes.get("product_id");
            marketingName = getInstalledMarketingName(id);
            return i18n.tr(keys.get(transKey), marketingName);
        }
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

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
        buildKeys();
    }

    private void buildKeys() {
        keys = new HashMap<String, String>() {
            {
                put("NOTCOVERED", i18n.tr(
                    "The system does not have subscriptions that cover {0}."));
                put("ARCH", i18n.tr(
                    "Subscriptions for {0} cover architecture {1} but the system is {2}."));
                put("SOCKETS",  i18n.tr(
                    "Subscriptions for {0} only cover {1} of {2} sockets."));
                put("CORES", i18n.tr(
                    "Subscriptions for {0} only cover {1} of {2} cores."));
                put("RAM", i18n.tr(
                    "Subscriptions for {0} only cover {1}gb of systems {2}gb of ram."));
                put("DEFAULT", i18n.tr(
                    "{3} COVERAGE PROBLEM.  Subscription for {0} covers {1} of {2}"));
            }
        };
    }

    private String getStackedMarketingName(String stackId) {
        for (Entitlement e : consumer.getEntitlements()) {
            if (e.getPool().getProductAttributes().contains("stacking_id") &&
                e.getPool().getProductAttribute("stacking_id").getValue().equals(stackId)) {
                return e.getPool().getProductName();
            }
        }
        return "UNABLE_TO_GET_NAME";
    }

    private String getMarketingName(String id) {
        for (Entitlement e : consumer.getEntitlements()) {
            if (e.getId().equals(id)) {
                return e.getPool().getProductName();
            }
        }
        return "UNABLE_TO_GET_NAME";
    }

    private String getInstalledMarketingName(String id) {
        for (ConsumerInstalledProduct prod : consumer.getInstalledProducts()) {
            if (prod.getId().equals(id)) {
                return prod.getProductName();
            }
        }
        return "UNABLE_TO_GET_NAME";
    }
}
