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

import java.util.stream.Stream;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.candlepin.dto.api.server.v1.RhsmApiConsumerEntitlementCountsDTO;
import org.candlepin.dto.api.server.v1.RhsmApiConsumerEntitlementCountsQueryDTO;
import org.candlepin.resource.server.v1.RhsmapiApi;

public class RhsmApiResource implements RhsmapiApi {

    // POST /rhsmapi/consumers/entitlement_counts

    @Override
    public Stream<RhsmApiConsumerEntitlementCountsDTO> listConsumerEntitlementCounts(
        @Valid @NotNull RhsmApiConsumerEntitlementCountsQueryDTO rhsmApiConsumerEntitlementCountsQueryDTO) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'listConsumerEntitlementCounts'");
    }

}
