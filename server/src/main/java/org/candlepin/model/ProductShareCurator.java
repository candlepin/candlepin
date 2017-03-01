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
import com.google.inject.persist.Transactional;

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

    @Transactional
    public ProductShare findProductShare(Owner owner, Product product, Owner recipient) {
        String jpql = "FROM ProductShare ps WHERE ps.product.uuid = :product_uuid " +
            "AND ps.owner.id = :owner_id AND ps.recipient.id = :recipient_id";

        TypedQuery<ProductShare> query = getEntityManager()
            .createQuery(jpql, ProductShare.class)
            .setParameter("product_uuid", product.getUuid())
            .setParameter("owner_id", owner.getId())
            .setParameter("recipient_id", recipient.getId());

        // An owner, recipient, and product should combine to be unique
        return getSingleResult(query);
    }

    @Transactional
    public List<ProductShare> findProductSharesByOwner(Owner owner, Product product) {
        String jpql = "FROM ProductShare ps WHERE ps.product.uuid = :product_uuid " +
            "AND ps.owner.id = :owner_id";

        TypedQuery<ProductShare> query = getEntityManager()
            .createQuery(jpql, ProductShare.class)
            .setParameter("product_uuid", product.getUuid())
            .setParameter("owner_id", owner.getId());

        return query.getResultList();
    }

    @Transactional
    public ProductShare findProductShareByRecipient(Owner recipient, String productId) {
        String jpql = "FROM ProductShare ps WHERE ps.recipient.id = :recipient_id " +
            "AND ps.product.id = :product_id";

        TypedQuery<ProductShare> query = getEntityManager()
            .createQuery(jpql, ProductShare.class)
            .setParameter("product_id", productId)
            .setParameter("recipient_id", recipient.getId());

        return getSingleResult(query);
    }

    @Transactional
    public List<ProductShare> findProductSharesBetweenOwners(Owner owner, Owner recipient) {
        String jpql = "FROM ProductShare ps WHERE ps.recipient.id = :recipient_id " +
            "AND ps.owner.id = :owner_id";

        TypedQuery<ProductShare> query = getEntityManager()
            .createQuery(jpql, ProductShare.class)
            .setParameter("recipient_id", recipient.getId())
            .setParameter("owner_id", owner.getId());

        return query.getResultList();
    }
}
