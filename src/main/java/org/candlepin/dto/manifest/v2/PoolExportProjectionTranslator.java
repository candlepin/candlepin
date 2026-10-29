/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

package org.candlepin.dto.manifest.v2;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.PoolExportProjection;

/**
 * Translation of pool export projections to version 2 manifest SubscriptionDTOs
 */
public class PoolExportProjectionTranslator implements ObjectTranslator<PoolExportProjection, SubscriptionDTO> {

    @Override
    public SubscriptionDTO translate(PoolExportProjection source) {
        return this.translate(null, source);
    }

    @Override
    public SubscriptionDTO translate(ModelTranslator translator, PoolExportProjection source) {
        return source != null ? this.populate(translator, source, new SubscriptionDTO()) : null;
    }

    @Override
    public SubscriptionDTO populate(PoolExportProjection source, SubscriptionDTO destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public SubscriptionDTO populate(ModelTranslator translator, PoolExportProjection source, SubscriptionDTO destination) {
        if (source == null || destination == null) {
            throw new IllegalArgumentException("source and destination must not be null");
        }

        destination.setCreated(source.created());
        destination.setUpdated(source.updated());

        destination.setSubscriptionId(source.subscriptionId());
        destination.setStartDate(source.startDate());
        destination.setEndDate(source.endDate());
        destination.setContractNumber(source.contractNumber());
        destination.setAccountNumber(source.accountNumber());
        destination.setProductId(source.productId());

        return destination;
    }
}
