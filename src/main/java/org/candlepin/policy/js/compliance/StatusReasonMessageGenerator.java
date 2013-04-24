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
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.Entitlement;
import org.xnap.commons.i18n.I18n;
import com.google.inject.Inject;

/**
 * StatusReasonMessageGenerator
 */
public class StatusReasonMessageGenerator {

    private static final Map<String, String> KEYS = new HashMap<String, String>() {
        {
            put("NOTCOVERED",
                "The system does not have subscriptions that cover {0}.");
            put("ARCH",
                "{0} covers architecture {1} but the system is {2}.");
            put("SOCKETS",
                "{0} only covers {1} of {2} sockets.");
            put("CORES",
                "{0} only covers {1} of {2} cores.");
            put("RAM",
                "{0} only covers {1}GB of {2}GB of RAM.");
            put("DEFAULT",
                "{3} COVERAGE PROBLEM.  Subscription for {0} covers {1} of {2}");
        }
    };

    private I18n i18n;

    @Inject
    public StatusReasonMessageGenerator(I18n i18n) {
        this.i18n = i18n;
    }

    public void setMessage(Consumer c, ComplianceReason reason) {
        String base = KEYS.get(reason.getKey());
        if (base == null) {
            base = KEYS.get("DEFAULT");
        }
        String marketingName, id;
        if (reason.isStacked()) {
            id = reason.getAttributes().get("stack_id");
            marketingName = getStackedMarketingName(id, c);
            reason.setMessage(i18n.tr(base, marketingName,
                reason.getAttributes().get("covered"),
                reason.getAttributes().get("has"), reason.getKey()));
        }
        else if (reason.isNonCovered()) {
            id = reason.getAttributes().get("product_id");
            marketingName = getInstalledMarketingName(id, c);
            reason.setMessage(i18n.tr(base, marketingName));
        }
        else { //nonstacked regular ent
            id = reason.getAttributes().get("entitlement_id");
            marketingName = getMarketingName(id, c);
            reason.setMessage(i18n.tr(base, marketingName,
                reason.getAttributes().get("covered"),
                reason.getAttributes().get("has"), reason.getKey()));
        }
    }

    private String getStackedMarketingName(String stackId, Consumer consumer) {
        String result = "";
        for (Entitlement e : consumer.getEntitlements()) {
            if (e.getPool().getProductAttribute("stacking_id") != null) {
                if (e.getPool().getProductAttribute("stacking_id")
                    .getValue().equals(stackId)) {
                    result += e.getPool().getProductName() + "/";
                }
            }
        }
        if (result.length() > 0) {
            return result.substring(0, result.length() - 1);
        }
        else {
            return "UNABLE_TO_GET_NAME";
        }
    }

    private String getMarketingName(String id, Consumer consumer) {
        for (Entitlement e : consumer.getEntitlements()) {
            if (e.getId().equals(id)) {
                return e.getPool().getProductName();
            }
        }
        return "UNABLE_TO_GET_NAME";
    }

    private String getInstalledMarketingName(String id, Consumer consumer) {
        for (ConsumerInstalledProduct prod : consumer.getInstalledProducts()) {
            if (prod.getProductId().equals(id)) {
                return prod.getProductName();
            }
        }
        return "UNABLE_TO_GET_NAME";
    }
}
