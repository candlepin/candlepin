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

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Product;

import com.google.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.xnap.commons.i18n.I18n;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * StatusReasonMessageGenerator
 */
public class StatusReasonMessageGenerator {

    private I18n i18n;

    private static final Map<String, String> REASON_MESSAGES;
    private static final String DEFAULT_REASON_MESSAGE;

    static {
        // Reason messages can use the following variables
        // {0} - reason key
        // {1} - value covered by entitlement
        // {2} - value present on consumer
        REASON_MESSAGES = new HashMap<>();

        REASON_MESSAGES.put(ComplianceReason.ReasonKeys.NOT_COVERED,
            I18n.marktr("Not supported by a valid subscription."));
        REASON_MESSAGES.put(ComplianceReason.ReasonKeys.ARCHITECTURE,
            I18n.marktr("Supports architecture {1} but the system is {2}."));
        REASON_MESSAGES.put(ComplianceReason.ReasonKeys.SOCKETS,
            I18n.marktr("Only supports {1} of {2} sockets."));
        REASON_MESSAGES.put(ComplianceReason.ReasonKeys.CORES,
            I18n.marktr("Only supports {1} of {2} cores."));
        REASON_MESSAGES.put(ComplianceReason.ReasonKeys.RAM,
            I18n.marktr("Only supports {1}GB of {2}GB of RAM."));
        REASON_MESSAGES.put(ComplianceReason.ReasonKeys.VIRT_LIMIT,
            I18n.marktr("Only supports {1} of {2} virtual guests."));
        REASON_MESSAGES.put(ComplianceReason.ReasonKeys.VIRT_CPU,
            I18n.marktr("Only supports {1} of {2} vCPUs."));
        REASON_MESSAGES.put(ComplianceReason.ReasonKeys.UNMAPPED_GUEST,
            I18n.marktr(
            "Guest has not been reported on any host and is using a temporary unmapped guest subscription." +
            " For more information, please see https://access.redhat.com/solutions/1592573"));
        REASON_MESSAGES.put(ComplianceReason.ReasonKeys.STORAGE_BAND,
            I18n.marktr("Only supports {1}TB of {2}TB of storage."));
        DEFAULT_REASON_MESSAGE =
            I18n.marktr("{0} COVERAGE PROBLEM.  Supports {1} of {2}");
    }

    private static String getReasonMessage(String key) {
        String message = REASON_MESSAGES.get(key);
        return message != null ? message : DEFAULT_REASON_MESSAGE;
    }

    @Inject
    public StatusReasonMessageGenerator(I18n i18n) {
        this.i18n = i18n;
    }

    public void setMessage(Consumer c, ComplianceReason reason, Date onDate) {
        String marketingName, id;

        if (reason.isStacked()) {
            id = reason.getAttributes().get(ComplianceReason.Attributes.STACKING_ID);
            marketingName = getStackedMarketingName(id, c, onDate);
            reason.getAttributes().put(ComplianceReason.Attributes.MARKETING_NAME, marketingName);
        }
        else if (reason.isNonCovered()) {
            id = reason.getAttributes().get(ComplianceReason.Attributes.PRODUCT_ID);
            marketingName = getInstalledMarketingName(id, c);
            reason.getAttributes().put(ComplianceReason.Attributes.MARKETING_NAME, marketingName);
        }
        else { //nonstacked regular ent
            id = reason.getAttributes().get(ComplianceReason.Attributes.ENTITLEMENT_ID);
            marketingName = getMarketingName(id, c, onDate);
            reason.getAttributes().put(ComplianceReason.Attributes.MARKETING_NAME, marketingName);
        }

        String key = reason.getKey();

        reason.setMessage(i18n.tr(getReasonMessage(key),
            key,
            reason.getAttributes().get(ComplianceReason.Attributes.COVERED),
            reason.getAttributes().get(ComplianceReason.Attributes.PRESENT)
        ));
    }

    private String getStackedMarketingName(String stackId, Consumer consumer, Date onDate) {
        Set<String> results = new HashSet<>();
        for (Entitlement e : getEntitlementsOnDate(consumer, onDate)) {
            String sid = e.getPool().getProduct().getAttributeValue(Product.Attributes.STACKING_ID);

            if (stackId != null && stackId.equals(sid)) {
                results.add(e.getPool().getProduct().getName());
            }
        }

        return results.size() > 0 ? StringUtils.join(results, "/") : "UNABLE_TO_GET_NAME";
    }

    private String getMarketingName(String id, Consumer consumer, Date onDate) {
        for (Entitlement e : getEntitlementsOnDate(consumer, onDate)) {
            if (e.getId() != null && e.getId().equals(id)) {
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

    private Set<Entitlement> getEntitlementsOnDate(Consumer c, Date onDate) {
        Set<Entitlement> result = new HashSet<>();
        for (Entitlement ent : c.getEntitlements()) {
            if (ent.isValidOnDate(onDate)) {
                result.add(ent);
            }
        }

        return result;
    }
}
