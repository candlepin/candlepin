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

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.dto.api.server.v1.ConsumerEntitlementCountsDTO;
import org.candlepin.dto.api.server.v1.RhsmApiConsumerEntitlementCountsQueryDTO;
import org.candlepin.dto.api.server.v1.RhsmApiEntitlementCountDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.ConsumerEntitlementCount;
import org.candlepin.model.RhsmApiCompatCurator;
import org.candlepin.paging.PageRequest;
import org.candlepin.paging.PageRequest.Order;
import org.candlepin.paging.PagingUtilFactory;
import org.candlepin.resource.server.v1.RhsmapiApi;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.jboss.resteasy.core.ResteasyContext;
import org.xnap.commons.i18n.I18n;

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
    private final PagingUtilFactory pagingUtilFactory;
    private final Configuration config;
    private final I18n i18n;

    private final int pageLimit;

    @Inject
    public RhsmApiCompatResource(RhsmApiCompatCurator rhsmApiCompatCurator,
        PagingUtilFactory pagingUtilFactory,
        Configuration config,
        I18n i18n) {

        this.rhsmApiCompatCurator = Objects.requireNonNull(rhsmApiCompatCurator);
        this.pagingUtilFactory = Objects.requireNonNull(pagingUtilFactory);
        this.config = Objects.requireNonNull(config);
        this.i18n = Objects.requireNonNull(i18n);

        this.pageLimit = this.config.getInt(ConfigProperties.RHSM_API_PAGE_LIMIT);
    }

    /**
     * POST /rhsmapi/consumers/entitlement_counts
     * <p><br>
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Stream<ConsumerEntitlementCountsDTO> listConsumerEntitlementCounts(
        @Valid @NotNull RhsmApiConsumerEntitlementCountsQueryDTO rhsmApiConsumerEntitlementCountsQueryDTO) {

        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);
        if (pageRequest != null) {
            if (pageRequest.getPerPage() < 1 || pageRequest.getPerPage() > this.pageLimit) {
                String msg = i18n.tr("Per page query parameter value must be larger than 0 and smaller " +
                    "than or equal to {0}.", this.pageLimit);
                throw new BadRequestException(msg);
            }

            // The sort by and order is static and not definable by the request
            pageRequest.setSortBy("consumerId");
            pageRequest.setOrder(Order.ASCENDING);
        }

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

            RhsmApiEntitlementCountDTO entitlementCountsItem =
                new RhsmApiEntitlementCountDTO()
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

        return pagingUtilFactory.forClass(ConsumerEntitlementCountsDTO.class)
            .applyPaging(consumerEntCountDTOs.stream(), consumerEntCountDTOs.size());
    }

}

