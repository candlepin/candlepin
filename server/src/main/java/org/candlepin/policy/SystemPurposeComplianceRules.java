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
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.policy.js.compliance.hash.ComplianceStatusHasher;

import com.google.inject.Inject;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
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

    private EventSink eventSink;
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private I18n i18n;

    @Inject
    public SystemPurposeComplianceRules(EventSink eventSink, ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator, I18n i18n) {

        this.eventSink = eventSink;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.i18n = i18n;
    }

    /**
     * Check system purpose compliance status for a consumer for the current date.
     *
     * @param consumer The consumer to check.
     * @param existingEntitlements The consumer's existing entitlements.
     * @param newEntitlements New entitlements of the consumer.
     * @param updateConsumer Whether or not to use consumerCurator.update
     *
     * @return The system purpose compliance status currently.
     */
    public SystemPurposeComplianceStatus getStatus(Consumer consumer,
        Collection<Entitlement> existingEntitlements, Collection<Entitlement> newEntitlements,
        boolean updateConsumer) {

        return getStatus(consumer, existingEntitlements, newEntitlements,
            null, updateConsumer);
    }

    /**
     * Check system purpose compliance status for a consumer on a specific date.
     *
     * @param consumer The consumer to check.
     * @param existingEntitlements The consumer's existing entitlements.
     * @param newEntitlements New entitlements of the consumer.
     * @param date The date to check compliance status for.
     * @param updateConsumer Whether or not to use consumerCurator.update
     *
     * @return The system purpose compliance status for the given date.
     */
    @SuppressWarnings({"checkstyle:indentation", "checkstyle:methodlength"})
    public SystemPurposeComplianceStatus getStatus(Consumer consumer,
        Collection<Entitlement> existingEntitlements, Collection<Entitlement> newEntitlements,
        Date date, boolean updateConsumer) {

        SystemPurposeComplianceStatus status = new SystemPurposeComplianceStatus(i18n);

        // Do not calculate compliance status for distributors. It is prohibitively
        // expensive and meaningless
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);
        if (ctype != null && (ctype.isManifest())) {
            return status;
        }

        if (consumer.getOwner() != null && consumer.getOwner().isContentAccessEnabled()) {
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

        // If we're calculating status for the current date, apply this change to the consumer & emit event.
        boolean currentCompliance = false;
        if (date == null) {
            currentCompliance = true;
            date = new Date();
        }

        // Filter the entitlements based on the given date.
        Date finalDate = date;
        entitlements.removeIf(element -> finalDate.compareTo(element.getStartDate()) < 0 ||
            finalDate.compareTo(element.getEndDate()) > 0);

        for (Entitlement entitlement : entitlements) {
            String unsatisfedRole = consumer.getRole();
            Set<String> unsatisfiedAddons = new HashSet<>(consumer.getAddOns());
            String preferredSla = consumer.getServiceLevel();
            String preferredUsage = consumer.getUsage();
            Pool entitlementPool = entitlement.getPool();

            Set<Product> entitlementProducts = new HashSet<>();

            if (entitlementPool.getProduct() != null &&
                entitlementPool.getProduct().getProvidedProducts() != null) {
                entitlementProducts.addAll(entitlementPool.getProduct().getProvidedProducts());
            }

            if (entitlementPool.getDerivedProduct() != null &&
                entitlementPool.getDerivedProduct().getProvidedProducts() != null) {
                entitlementProducts.addAll(entitlementPool.getDerivedProduct().getProvidedProducts());
            }

            entitlementProducts.add(entitlementPool.getProduct());
            entitlementProducts.add(entitlementPool.getDerivedProduct());
            entitlementProducts.remove(null);

            for (Product product : entitlementProducts) {
                if (StringUtils.isNotEmpty(unsatisfedRole) &&
                    product.hasAttribute(Product.Attributes.ROLES)) {
                    List<String> roles =
                        Arrays.asList(product.getAttributeValue(Product.Attributes.ROLES)
                        .trim().split("\\s*,\\s*"));

                    final String unsatisfedRoleFinalCopy = unsatisfedRole;
                    if (roles.stream()
                        .anyMatch(str -> str.equalsIgnoreCase(unsatisfedRoleFinalCopy.trim()))) {
                        status.addCompliantRole(unsatisfedRole, entitlement);
                        unsatisfedRole = null;
                    }
                }

                Set<String> addonsFound = new HashSet<>();
                if (CollectionUtils.isNotEmpty(unsatisfiedAddons) &&
                    product.hasAttribute(Product.Attributes.ADDONS)) {
                    List<String> addOns =
                        Arrays.asList(product.getAttributeValue(Product.Attributes.ADDONS)
                        .trim().split("\\s*,\\s*"));
                    for (String addOn: unsatisfiedAddons) {
                        if (addOns.stream().anyMatch(str -> str.equalsIgnoreCase(addOn.trim()))) {
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
