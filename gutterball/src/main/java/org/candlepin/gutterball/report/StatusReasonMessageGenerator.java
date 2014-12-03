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
package org.candlepin.gutterball.report;

import org.candlepin.gutterball.model.snapshot.ComplianceReason;
import org.candlepin.gutterball.model.snapshot.Consumer;
import org.candlepin.gutterball.model.snapshot.ConsumerInstalledProduct;
import org.candlepin.gutterball.model.snapshot.Entitlement;

import com.google.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.xnap.commons.i18n.I18n;

import java.util.HashSet;
import java.util.Set;

/**
 * StatusReasonMessageGenerator
 */
public class StatusReasonMessageGenerator {

    private I18n i18n;

    @Inject
    public StatusReasonMessageGenerator(I18n i18n) {
        this.i18n = i18n;
    }

    public void setMessage(Consumer c, ComplianceReason reason) {
        String marketingName, id;
        if (reason.isStacked()) {
            id = reason.getAttributes().get("stack_id");
            marketingName = getStackedMarketingName(id, c);
            reason.getAttributes().put("name", marketingName);
        }
        else if (reason.isNonCovered()) {
            id = reason.getAttributes().get("product_id");
            marketingName = getInstalledMarketingName(id, c);
            reason.getAttributes().put("name", marketingName);
        }
        else { //nonstacked regular ent
            id = reason.getAttributes().get("entitlement_id");
            marketingName = getMarketingName(id, c);
            reason.getAttributes().put("name", marketingName);
        }
        if (reason.getKey().equals("NOTCOVERED")) {
            reason.setMessage(i18n.tr("Not supported by a valid subscription."));
        }
        else if (reason.getKey().equals("ARCH")) {
            reason.setMessage(i18n.tr("Supports architecture {0} but the system is {1}.",
                reason.getAttributes().get("covered"),
                reason.getAttributes().get("has")));
        }
        else if (reason.getKey().equals("SOCKETS")) {
            reason.setMessage(i18n.tr("Only supports {0} of {1} sockets.",
                reason.getAttributes().get("covered"),
                reason.getAttributes().get("has")));
        }
        else if (reason.getKey().equals("CORES")) {
            reason.setMessage(i18n.tr("Only supports {0} of {1} cores.",
                reason.getAttributes().get("covered"),
                reason.getAttributes().get("has")));
        }
        else if (reason.getKey().equals("RAM")) {
            reason.setMessage(i18n.tr("Only supports {0}GB of {1}GB of RAM.",
                reason.getAttributes().get("covered"),
                reason.getAttributes().get("has")));
        }
        else if (reason.getKey().equals("GUEST_LIMIT")) {
            reason.setMessage(i18n.tr("Only supports {0} of {1} virtual guests.",
                reason.getAttributes().get("covered"),
                reason.getAttributes().get("has")));
        }
        else if (reason.getKey().equals("VCPU")) {
            reason.setMessage(i18n.tr("Only supports {0} of {1} vCPUs.",
                reason.getAttributes().get("covered"),
                reason.getAttributes().get("has")));
        }
        else { //default fallback
            reason.setMessage(i18n.tr("{2} COVERAGE PROBLEM.  Supports {0} of {1}",
                reason.getAttributes().get("covered"),
                reason.getAttributes().get("has"), reason.getKey()));
        }
    }

    private String getStackedMarketingName(String stackId, Consumer consumer) {
        Set<String> results = new HashSet<String>();
        for (Entitlement e : consumer.getComplianceSnapshot().getEntitlements()) {
            if (e.getAttributes().get("stacking_id") != null) {
                if (e.getAttributes().get("stacking_id").equals(stackId)) {
                    results.add(e.getProductName());
                }
            }
        }
        if (results.size() > 0) {
            return StringUtils.join(results, "/");
        }
        else {
            return "UNABLE_TO_GET_NAME";
        }
    }

    private String getMarketingName(String id, Consumer consumer) {
        for (Entitlement e : consumer.getComplianceSnapshot().getEntitlements()) {
            if (e.getId() != null && e.getId().equals(id)) {
                return e.getProductName();
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
