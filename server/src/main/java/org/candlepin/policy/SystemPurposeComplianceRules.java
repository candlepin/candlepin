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
package org.candlepin.policy;

import org.candlepin.audit.EventSink;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Product;
import org.candlepin.policy.js.compliance.StatusReasonMessageGenerator;
import org.candlepin.policy.js.compliance.hash.ComplianceStatusHasher;

import com.google.inject.Inject;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.candlepin.service.ContentAccessCertServiceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ComplianceRules
 *
 * A class used to check consumer compliance status.
 */
public class SystemPurposeComplianceRules {
    private static Logger log = LoggerFactory.getLogger(SystemPurposeComplianceRules.class);

    private EntitlementCurator entCurator;
    private StatusReasonMessageGenerator generator;
    private EventSink eventSink;
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private I18n i18n;

    @Inject
    public SystemPurposeComplianceRules(EntitlementCurator entCurator,
        StatusReasonMessageGenerator generator, EventSink eventSink, ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator, I18n i18n) {

        this.entCurator = entCurator;
        this.generator = generator;
        this.eventSink = eventSink;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.i18n = i18n;
    }

    /**
     * Check compliance status for a consumer on a specific date.
     *
     * @param consumer Consumer to check.
     * @param updateConsumer whether or not to use consumerCurator.update
     *        (also expensive)
     * @return Compliance status.
     */
    @SuppressWarnings("checkstyle:indentation")
    public SystemPurposeComplianceStatus getStatus(Consumer consumer,
        Collection<Entitlement> existingEntitlements, Collection<Entitlement> newEntitlements,
        boolean updateConsumer, boolean currentCompliance) {

        SystemPurposeComplianceStatus status = new SystemPurposeComplianceStatus(i18n);

        if (consumer.getOwner().getContentAccessMode().equals(
            ContentAccessCertServiceAdapter.ORG_ENV_ACCESS_MODE)) {
            status.setDisabled(true);
            applyStatus(consumer, status, updateConsumer);
            return status;
        }

        if (existingEntitlements == null) {
            existingEntitlements = new LinkedList<>();
        }

        if (newEntitlements == null) {
            newEntitlements = new LinkedList<>();
        }

        Set<Entitlement> entitlements = Stream.concat(
            existingEntitlements.stream(),
            newEntitlements.stream())
            .collect(Collectors.toSet());

        for (Entitlement entitlement : entitlements) {
            String unsatisfedRole = consumer.getRole();
            Set<String> unsatisfiedAddons = new HashSet<>(consumer.getAddOns());
            String preferredSla = consumer.getServiceLevel();
            String preferredUsage = consumer.getUsage();

            Set<Product> products = Stream.concat(
                entitlement.getPool().getProvidedProducts().stream(),
                entitlement.getPool().getDerivedProvidedProducts().stream()
            ).collect(Collectors.toSet());
            products.add(entitlement.getPool().getProduct());
            Product derivedProduct = entitlement.getPool().getDerivedProduct();
            if (derivedProduct != null) {
                products.add(derivedProduct);
            }

            for (Product product : products) {
                if (StringUtils.isNotEmpty(unsatisfedRole) &&
                    product.hasAttribute(Product.Attributes.ROLES)) {
                    List<String> roles =
                        Arrays.asList(product.getAttributeValue(Product.Attributes.ROLES).split("\\s*,\\s*"));
                    if (roles.contains(consumer.getRole())) {
                        status.addCompliantRole(consumer.getRole(), entitlement);
                        unsatisfedRole = null;
                    }
                }

                Set<String> addonsFound = new HashSet<>();
                if (CollectionUtils.isNotEmpty(unsatisfiedAddons) &&
                    product.hasAttribute(Product.Attributes.ADDONS)) {
                    List<String> addOns =
                        Arrays.asList(product.getAttributeValue(Product.Attributes.ADDONS)
                            .split("\\s*,\\s*"));
                    for (String addOn: unsatisfiedAddons) {
                        if (addOns.stream().anyMatch(str -> str.trim().equals(addOn))) {
                            status.addCompliantAddOn(addOn, entitlement);
                            addonsFound.add(addOn);
                        }
                    }
                }
                unsatisfiedAddons.removeAll(addonsFound);

                if (StringUtils.isNotEmpty(preferredSla) &&
                    product.hasAttribute(Product.Attributes.SUPPORT_LEVEL)) {
                    String sla = product.getAttributeValue(Product.Attributes.SUPPORT_LEVEL);
                    if (sla.equalsIgnoreCase(preferredSla)) {
                        status.addCompliantSLA(sla, entitlement);
                    }
                }

                if (StringUtils.isNotEmpty(preferredUsage) &&
                    product.hasAttribute(Product.Attributes.USAGE)) {
                    String usage = product.getAttributeValue(Product.Attributes.USAGE);
                    if (usage.equalsIgnoreCase(preferredUsage)) {
                        status.addCompliantUsage(usage, entitlement);
                    }
                }
            }
        }

        // check if there are unsatisfied role, add ons, slas and usage
        if (StringUtils.isNotEmpty(consumer.getRole()) &&
            !status.getCompliantRole().containsKey(consumer.getRole())) {
            status.setNonCompliantRole(consumer.getRole());
        }
        if (CollectionUtils.isNotEmpty(consumer.getAddOns())) {
            for (String addOn : consumer.getAddOns()) {
                if (!status.getCompliantAddOns().containsKey(addOn)) {
                    status.addNonCompliantAddOn(addOn);
                }
            }
        }
        if (StringUtils.isNotEmpty(consumer.getServiceLevel()) && status.getCompliantSLA().isEmpty()) {
            status.setNonCompliantSLA(consumer.getServiceLevel());
        }
        if (StringUtils.isNotEmpty(consumer.getUsage()) && status.getCompliantUsage().isEmpty()) {
            status.setNonCompliantUsage(consumer.getUsage());
        }

        if (currentCompliance) {
            applyStatus(consumer, status, updateConsumer);
        }

        return status;
    }

    public void applyStatus(Consumer c, SystemPurposeComplianceStatus status, boolean updateConsumer) {
        String newHash = getComplianceStatusHash(status, c);
        boolean complianceChanged = !newHash.equals(c.getComplianceStatusHash());
        if (complianceChanged) {
            log.debug("System purpose compliance has changed, sending Compliance event.");
            c.setComplianceStatusHash(newHash);
            eventSink.emitCompliance(c, status);
        }

        boolean statusChanged = !status.getStatus().equals(c.getSystemPurposeStatus());
        if (statusChanged) {
            c.setSystemPurposeStatus(status.getStatus());
        }

        if (updateConsumer && (complianceChanged || statusChanged)) {
            // Merge might work better here, but we use update in other places for this
            consumerCurator.update(c, false);
        }
    }

    private String getComplianceStatusHash(SystemPurposeComplianceStatus status, Consumer consumer) {
        ComplianceStatusHasher hasher = new ComplianceStatusHasher(consumer, status);
        return hasher.hash();
    }


}
