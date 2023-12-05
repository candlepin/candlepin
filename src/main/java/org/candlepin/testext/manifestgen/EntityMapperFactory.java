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
package org.candlepin.testext.manifestgen;

import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.ProductCurator;

import java.util.Objects;

import javax.inject.Inject;



public class EntityMapperFactory {

    private final PoolCurator poolCurator;
    private final ProductCurator productCurator;
    private final ContentCurator contentCurator;

    @Inject
    public EntityMapperFactory(PoolCurator poolCurator, ProductCurator productCurator,
        ContentCurator contentCurator) {

        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.productCurator = Objects.requireNonNull(productCurator);
        this.contentCurator = Objects.requireNonNull(contentCurator);
    }

    public EntityMapper getForOwner(Owner owner) {
        return new EntityMapper(owner, this.poolCurator, this.productCurator, this.contentCurator);
    }

}
