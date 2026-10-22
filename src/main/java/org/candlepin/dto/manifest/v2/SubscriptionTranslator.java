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
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.Pool;

/**
 * Translation of Pool objects to version 2 manifest SubscriptionDTOs
 */
public class SubscriptionTranslator extends TimestampedEntityTranslator<Pool, SubscriptionDTO> {

    @Override
    public SubscriptionDTO translate(Pool source) {
        return this.translate(null, source);
    }

    @Override
    public SubscriptionDTO translate(ModelTranslator translator, Pool source) {
        return source != null ? this.populate(translator, source, new SubscriptionDTO()) : null;
    }

    @Override
    public SubscriptionDTO populate(Pool source, SubscriptionDTO destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public SubscriptionDTO populate(ModelTranslator translator, Pool source, SubscriptionDTO destination) {
        destination = super.populate(translator, source, destination);

        destination.setSubscriptionId(source.getSubscriptionId());
        destination.setStartDate(source.getStartDate());
        destination.setEndDate(source.getEndDate());
        destination.setContractNumber(source.getContractNumber());
        destination.setAccountNumber(source.getAccountNumber());
        destination.setProductId(source.getProductId());

        return destination;
    }
}
