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
package org.candlepin.util;

import java.util.HashSet;
import java.util.Set;

import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

/**
 * ConsumerResourcesUtil class to hold code that needs
 * to be shared between counsumer resource and sub-resources
 */
public class ConsumerResourcesUtil {

    private static Logger log = LoggerFactory.getLogger(ConsumerResourcesUtil.class);

    private I18n i18n;
    private PoolManager poolManager;

    public ConsumerResourcesUtil(PoolManager poolManager, I18n i18n) {
        this.i18n = i18n;
        this.poolManager = poolManager;
    }

    public void checkServiceLevel(Owner owner, String serviceLevel)
        throws BadRequestException {
        if (serviceLevel != null &&
            !serviceLevel.trim().equals("")) {
            for (String level : poolManager.retrieveServiceLevelsForOwner(owner, false)) {
                if (serviceLevel.equalsIgnoreCase(level)) {
                    return;
                }
            }
            throw new BadRequestException(
                i18n.tr(
                    "Service level ''{0}'' is not available " +
                    "to units of organization {1}.",
                    serviceLevel, owner.getKey()));
        }
    }

    public void revokeGuestEntitlementsNotMatchingHost(Consumer host, Consumer guest) {
        // we need to create a list of entitlements to delete before actually
        // deleting, otherwise we are tampering with the loop iterator (BZ #786730)
        Set<Entitlement> deletableGuestEntitlements = new HashSet<Entitlement>();
        log.debug("Revoking {} entitlements not matching host: {}", guest, host);
        for (Entitlement entitlement : guest.getEntitlements()) {
            Pool pool = entitlement.getPool();

            // If there is no host required, do not revoke the entitlement.
            if (!pool.hasAttribute("requires_host")) {
                continue;
            }

            String requiredHost = getRequiredHost(pool);
            if (isVirtOnly(pool) && !requiredHost.equals(host.getUuid())) {
                log.warn("Removing entitlement " + entitlement.getProductId() +
                    " from guest " + guest.getName());
                deletableGuestEntitlements.add(entitlement);
            }
            else {
                log.info("Entitlement " + entitlement.getProductId() +
                         " on " + guest.getName() +
                         " is still valid, and will not be removed.");
            }
        }
        // perform the entitlement revocation
        for (Entitlement entitlement : deletableGuestEntitlements) {
            poolManager.revokeEntitlement(entitlement);
        }

    }

    private String getRequiredHost(Pool pool) {
        return pool.hasAttribute("requires_host") ?
            pool.getAttributeValue("requires_host") : "";
    }

    private boolean isVirtOnly(Pool pool) {
        String virtOnly = pool.hasAttribute("virt_only") ?
            pool.getAttributeValue("virt_only") : "false";
        return virtOnly.equalsIgnoreCase("true") || virtOnly.equals("1");
    }
}
