/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.Entitlement;
import org.candlepin.policy.js.compliance.ComplianceReason;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.util.Util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;



/**
 * The ComplianceStatusTranslator is used to translate ComplianceStatus objects to
 * ComplianceStatusDTOs.
 */
public class ComplianceStatusTranslator implements ObjectTranslator<ComplianceStatus, ComplianceStatusDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplianceStatusDTO translate(ComplianceStatus source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplianceStatusDTO translate(ModelTranslator translator, ComplianceStatus source) {
        return source != null ? this.populate(translator, source, new ComplianceStatusDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplianceStatusDTO populate(ComplianceStatus source, ComplianceStatusDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplianceStatusDTO populate(ModelTranslator translator, ComplianceStatus source,
        ComplianceStatusDTO destination) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }

        destination.setStatus(source.getStatus());
        destination.setCompliant(source.isCompliant());

        destination.setDate(Util.toDateTime(source.getDate()));
        destination.setCompliantUntil(Util.toDateTime(source.getCompliantUntil()));

        destination.setNonCompliantProducts(source.getNonCompliantProducts());
        destination.setProductComplianceDateRanges(source.getProductComplianceDateRanges());

        if (translator != null) {
            // Gather all of the entitlements so we can avoid any re-translation
            Set<Entitlement> entitlements = new HashSet<>();
            Map<String, EntitlementDTO> translated = new HashMap<>();

            Map<String, Set<Entitlement>> compliantProducts = source.getCompliantProducts();
            Map<String, Set<Entitlement>> pcProducts = source.getPartiallyCompliantProducts();
            Map<String, Set<Entitlement>> partialStacks = source.getPartialStacks();

            if (compliantProducts != null) {
                compliantProducts.values().forEach(entitlements::addAll);
            }

            if (pcProducts != null) {
                pcProducts.values().forEach(entitlements::addAll);
            }

            if (partialStacks != null) {
                partialStacks.values().forEach(entitlements::addAll);
            }

            // TODO: Use the bulk translation once available
            for (Entitlement entitlement : entitlements) {
                EntitlementDTO dto = translator.translate(entitlement, EntitlementDTO.class);

                if (dto != null) {
                    translated.put(dto.getId(), dto);
                }
            }

            // Rebuild the translated maps
            destination.setCompliantProducts(this.translateEntitlementMap(compliantProducts, translated));
            destination.setPartiallyCompliantProducts(this.translateEntitlementMap(pcProducts, translated));
            destination.setPartialStacks(this.translateEntitlementMap(partialStacks, translated));

            // Convert reasons
            // TODO: Convert this over to the bulk DTO translation once that's available
            Set<ComplianceReason> reasons = source.getReasons();
            if (reasons != null) {
                destination.setReasons(reasons.stream()
                    .filter(Objects::nonNull)
                    .map(e -> translator.translate(e, ComplianceReasonDTO.class))
                    .collect(Collectors.toSet()));
            }
        }
        else {
            destination.setCompliantProducts(null);
            destination.setPartiallyCompliantProducts(null);
            destination.setPartialStacks(null);
            destination.setReasons(null);
        }

        return destination;
    }

    /**
     * Rebuilds a translated map of entitlement sets from the source map and given mapping of
     * translated entitlement DTOs.
     *
     * @param sourceMap
     *  The source mapping of product ID to entitlement set
     *
     * @param dtos
     *  A mapping of entitlement ID to entitlement DTO to use for rebuilding the entitlement map
     *
     * @return
     *  The rebuilt and translated entitlement map, or null if the source map is null
     */
    private Map<String, Set<EntitlementDTO>> translateEntitlementMap(Map<String, Set<Entitlement>> sourceMap,
        Map<String, EntitlementDTO> dtos) {

        Map<String, Set<EntitlementDTO>> output = null;

        if (sourceMap != null) {
            output = new HashMap<>();

            for (Map.Entry<String, Set<Entitlement>> entry : sourceMap.entrySet()) {
                if (entry.getValue() != null) {
                    output.put(entry.getKey(), entry.getValue().stream()
                        .filter(Objects::nonNull)
                        .map(e -> dtos.get(e.getId()))
                        .collect(Collectors.toSet()));
                }
            }
        }

        return output;
    }
}
