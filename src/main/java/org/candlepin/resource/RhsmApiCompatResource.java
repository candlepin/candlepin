/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.resource;

import org.candlepin.dto.api.server.v1.ConsumerEntitlementCountsDTO;
import org.candlepin.dto.api.server.v1.ConsumerEntitlementCountsDTOAllOfEntitlementCounts;
import org.candlepin.dto.api.server.v1.RhsmApiConsumerEntitlementCountsQueryDTO;
import org.candlepin.model.ConsumerEntitlementCount;
import org.candlepin.model.RhsmApiCompatCurator;
import org.candlepin.resource.server.v1.RhsmapiApi;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Entry point for endpoints that are dedicated for RHSM API for compatibility reasons.
 */
public class RhsmApiCompatResource implements RhsmapiApi {
    private final RhsmApiCompatCurator rhsmApiCompatCurator;

    @Inject
    public RhsmApiCompatResource(RhsmApiCompatCurator rhsmApiCompatCurator) {
        this.rhsmApiCompatCurator = Objects.requireNonNull(rhsmApiCompatCurator);
    }

    // POST /rhsmapi/consumers/entitlement_counts

    @Override
    @Transactional
    public Stream<ConsumerEntitlementCountsDTO> listConsumerEntitlementCounts(
        @Valid @NotNull RhsmApiConsumerEntitlementCountsQueryDTO rhsmApiConsumerEntitlementCountsQueryDTO,
        Integer page, Integer perPage) {

        List<String> consumerIds = rhsmApiConsumerEntitlementCountsQueryDTO.getConsumerIds();
        List<String> consumerUuids = rhsmApiConsumerEntitlementCountsQueryDTO.getConsumerUuids();

        List<ConsumerEntitlementCount> consumerEntCounts = this.rhsmApiCompatCurator
            .getConsumerEntitlementCounts(consumerIds, consumerUuids);

        // The order of the ConsumerEntitlementCountsDTOs needs to be in the same order as consumerEntCounts
        List<ConsumerEntitlementCountsDTO> consumerEntCountDTOs = new ArrayList<>();
        ConsumerEntitlementCountsDTO current = null;
        for (ConsumerEntitlementCount consumerEntCount : consumerEntCounts) {
            // Base case for the first iteration
            if (current == null) {
                current = new ConsumerEntitlementCountsDTO()
                    .consumerId(consumerEntCount.id())
                    .consumerUuid(consumerEntCount.uuid());
            }

            // We are now listing entitlement counts for a different consumer, so we can start populating
            // a new ConsumerEntitlementCountsDTO for this consumer
            if (!current.getConsumerId().equals(consumerEntCount.id())) {
                consumerEntCountDTOs.add(current);

                current = new ConsumerEntitlementCountsDTO()
                    .consumerId(consumerEntCount.id())
                    .consumerUuid(consumerEntCount.uuid());
            }

            ConsumerEntitlementCountsDTOAllOfEntitlementCounts entitlementCountsItem =
                new ConsumerEntitlementCountsDTOAllOfEntitlementCounts()
                    .contractNumber(consumerEntCount.contractNumber())
                    .subscriptionId(consumerEntCount.subscriptionId())
                    .productId(consumerEntCount.productId())
                    .productName(consumerEntCount.productName())
                    .count((int) consumerEntCount.quantity());

            current.addEntitlementCountsItem(entitlementCountsItem);
        }

        // Add the last item
        if (current != null) {
            consumerEntCountDTOs.add(current);
        }

        if (page != null && perPage != null) {
            int startIndex = (page - 1) * perPage;
            int endIndex = startIndex + perPage;

            return consumerEntCountDTOs.subList(startIndex, endIndex)
                .stream();
        }

        return consumerEntCountDTOs.stream();
    }

}

