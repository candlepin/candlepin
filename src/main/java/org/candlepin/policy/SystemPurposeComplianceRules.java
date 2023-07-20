/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.policy.js.compliance.hash.ComplianceStatusHasher;
import org.candlepin.util.Util;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;



/**
 * ComplianceRules
 *
 * A class used to check consumer compliance status.
 */
public class SystemPurposeComplianceRules {
    private static final Logger log = LoggerFactory.getLogger(SystemPurposeComplianceRules.class);

    private final EventSink eventSink;
    private final ConsumerCurator consumerCurator;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final I18n i18n;
    private final PoolCurator poolCurator;

    @Inject
    public SystemPurposeComplianceRules(EventSink eventSink, ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator, I18n i18n, PoolCurator poolCurator) {

        this.eventSink = Objects.requireNonNull(eventSink);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.i18n = Objects.requireNonNull(i18n);
        this.poolCurator = Objects.requireNonNull(poolCurator);
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
    @SuppressWarnings({ "checkstyle:indentation", "checkstyle:methodlength" })
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

        if (consumer.getOwner() != null && consumer.getOwner().isUsingSimpleContentAccess()) {
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

        // fetch SLA of the Owner
        Set<String> levels = poolCurator.retrieveServiceLevelsForOwner(consumer.getOwner(), true);
        boolean slaExempted = false;
        if (!levels.isEmpty() && !StringUtils.isBlank(consumer.getServiceLevel()) &&
            levels.contains(consumer.getServiceLevel())) {
            log.debug("Ignoring due to SLA is layered and exempted true");
            slaExempted = true;
        }

        for (Entitlement entitlement : entitlements) {
            String unsatisfedRole = consumer.getRole();
            Set<String> unsatisfiedAddons = new HashSet<>(consumer.getAddOns());
            String preferredSla = consumer.getServiceLevel();
            String preferredUsage = consumer.getUsage();
            String preferedServiceType = consumer.getServiceType();
            Pool entitlementPool = entitlement.getPool();

            Set<Product> entitlementProducts = new HashSet<>();

            // FIXME: This doesn't properly support N-tier, and will fail to get anything below the
            // first set of children.
            Product poolProduct = entitlementPool.getProduct();
            if (poolProduct != null) {
                entitlementProducts.add(poolProduct);

                Collection<Product> provided = poolProduct.getProvidedProducts();
                if (provided != null) {
                    entitlementProducts.addAll(provided);
                }

                // FIXME: Do we actually want to fetch derived products and derived provided products here?
                Product poolDerived = poolProduct.getDerivedProduct();
                if (poolDerived != null) {
                    entitlementProducts.add(poolDerived);

                    provided = poolDerived.getProvidedProducts();
                    if (provided != null) {
                        entitlementProducts.addAll(provided);
                    }
                }
            }

            for (Product product : entitlementProducts) {
                if (StringUtils.isNotEmpty(unsatisfedRole) &&
                    product.hasAttribute(Product.Attributes.ROLES)) {
                    List<String> roles = Util.toList(product.getAttributeValue(Product.Attributes.ROLES));

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
                    List<String> addOns = Util.toList(product.getAttributeValue(Product.Attributes.ADDONS));
                    for (String addOn : unsatisfiedAddons) {
                        if (addOns.stream().anyMatch(str -> str.equalsIgnoreCase(addOn.trim()))) {
                            status.addCompliantAddOn(addOn, entitlement);
                            addonsFound.add(addOn);
                        }
                    }
                }
                unsatisfiedAddons.removeAll(addonsFound);

                String productSLA = product.getAttributeValue(Product.Attributes.SUPPORT_LEVEL);
                if (!slaExempted &&
                    StringUtils.isNotEmpty(preferredSla) &&
                    preferredSla.equalsIgnoreCase(productSLA)) {
                    status.addCompliantSLA(productSLA, entitlement);
                }

                String productUsage = product.getAttributeValue(Product.Attributes.USAGE);
                if (StringUtils.isNotEmpty(preferredUsage) &&
                    preferredUsage.equalsIgnoreCase(productUsage)) {
                    status.addCompliantUsage(productUsage, entitlement);
                }

                String productServiceType = product.getAttributeValue(Product.Attributes.SUPPORT_TYPE);
                if (StringUtils.isNotEmpty(preferedServiceType) &&
                    preferedServiceType.equalsIgnoreCase(productServiceType)) {
                    status.addCompliantServiceType(productServiceType, entitlement);
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

        if (!slaExempted &&
            StringUtils.isNotEmpty(consumer.getServiceLevel()) &&
            status.getCompliantSLA() != null && status.getCompliantSLA().isEmpty()) {
            status.setNonCompliantSLA(consumer.getServiceLevel());
        }

        if (StringUtils.isNotEmpty(consumer.getUsage()) &&
            status.getCompliantUsage() != null && status.getCompliantUsage().isEmpty()) {
            status.setNonCompliantUsage(consumer.getUsage());
        }

        if (StringUtils.isNotEmpty(consumer.getServiceType()) &&
            status.getCompliantServiceType() != null && status.getCompliantServiceType().isEmpty()) {
            status.setNonCompliantServiceType(consumer.getServiceType());
        }

        if (currentCompliance) {
            applyStatus(consumer, status, updateConsumer);
        }

        return status;
    }

    public void applyStatus(Consumer c, SystemPurposeComplianceStatus status, boolean updateConsumer) {
        String newHash = getSystemPurposeComplianceStatusHash(status, c);
        boolean systemPurposeComplianceChanged = !newHash.equals(c.getSystemPurposeStatusHash());
        if (systemPurposeComplianceChanged) {
            log.debug("System purpose compliance has changed, sending System purpose event.");
            c.setSystemPurposeStatusHash(newHash);
            eventSink.emitCompliance(c, status);
        }

        boolean statusChanged = !status.getStatus().equals(c.getSystemPurposeStatus());
        if (statusChanged) {
            c.setSystemPurposeStatus(status.getStatus());
        }

        if (updateConsumer && (systemPurposeComplianceChanged || statusChanged)) {
            // Merge might work better here, but we use update in other places for this
            consumerCurator.update(c, false);
        }
    }

    private String getSystemPurposeComplianceStatusHash(SystemPurposeComplianceStatus status,
        Consumer consumer) {
        ComplianceStatusHasher hasher = new ComplianceStatusHasher(consumer, status);
        return hasher.hash();
    }

}
