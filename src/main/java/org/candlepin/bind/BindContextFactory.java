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
package org.candlepin.bind;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PoolCurator;

import org.xnap.commons.i18n.I18n;

import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;



public class BindContextFactory {

    private final PoolCurator poolCurator;
    private final ConsumerCurator consumerCurator;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final OwnerCurator ownerCurator;
    private final I18n i18n;

    @Inject
    public BindContextFactory(PoolCurator poolCurator, ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator, OwnerCurator ownerCurator, I18n i18n) {
        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.i18n = Objects.requireNonNull(i18n);
    }

    public BindContext create(Consumer consumer, Map<String, Integer> quantities) {
        return new BindContext(
            poolCurator,
            consumerCurator,
            consumerTypeCurator,
            ownerCurator,
            i18n,
            consumer,
            quantities);
    }

}
