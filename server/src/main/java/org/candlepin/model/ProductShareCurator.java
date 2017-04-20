/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.model;

import com.google.inject.Inject;

import java.util.Collection;
import java.util.List;

import javax.persistence.TypedQuery;

/**
 * Curator to handle creation and maintenance of ProductShare objects
 */
public class ProductShareCurator extends AbstractHibernateCurator<ProductShare> {

    @Inject
    public ProductShareCurator() {
        super(ProductShare.class);
    }

    public List<ProductShare> findProductSharesByRecipient(Owner owner, Collection<String> productIds) {
        String jpql = "FROM ProductShare ps WHERE ps.product.id in (:product_ids) " +
            "AND ps.recipient.id = :owner_id";

        TypedQuery<ProductShare> query = getEntityManager()
            .createQuery(jpql, ProductShare.class)
            .setParameter("product_ids", productIds)
            .setParameter("owner_id", owner.getId());

        return query.getResultList();
    }
}
