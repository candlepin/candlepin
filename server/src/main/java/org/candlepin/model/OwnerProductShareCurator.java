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

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.collections.CollectionUtils;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.Query;
import javax.persistence.TypedQuery;

/**
 * Curator to handle creation and maintenance of OwnerProductShare objects
 */
public class OwnerProductShareCurator extends AbstractHibernateCurator<OwnerProductShare> {

    @Inject
    public OwnerProductShareCurator() {
        super(OwnerProductShare.class);
    }

    public List<OwnerProductShare> findProductSharesByRecipient(Owner owner, boolean activeOnly,
        Collection<String> productIds) {
        return findProductSharesByOwner(owner, true, activeOnly, productIds);
    }

    public List<OwnerProductShare> findProductSharesBySharer(Owner owner, boolean activeOnly,
        Collection<String> productIds) {
        return findProductSharesByOwner(owner, false, activeOnly, productIds);
    }

    private List<OwnerProductShare> findProductSharesByOwner(Owner owner, boolean isRecipient,
        boolean activeOnly, Collection<String> productIds) {

        String jpql = "FROM OwnerProductShare ps WHERE ";
        if (CollectionUtils.isNotEmpty(productIds)) {
            jpql += "ps.productId in (:product_ids) AND ";
        }

        if (isRecipient) {
            jpql += "ps.recipientOwner.id = :owner_id ";
        }
        else {
            jpql += "ps.sharingOwner.id = :owner_id ";
        }

        if (activeOnly) {
            jpql += "AND ps.active = true";
        }

        TypedQuery<OwnerProductShare> query = getEntityManager()
            .createQuery(jpql, OwnerProductShare.class)
            .setParameter("owner_id", owner.getId());

        List<OwnerProductShare> shares = new LinkedList<OwnerProductShare>();
        if (CollectionUtils.isNotEmpty(productIds)) {
            for (List<String> block : Iterables.partition(productIds, getInBlockSize())) {
                query.setParameter("product_ids", block);
                shares.addAll(query.getResultList());
            }
        }
        else {
            shares.addAll(query.getResultList());
        }
        return shares;
    }

    @Transactional
    public boolean removeShares(List<OwnerProductShare> shares) {
        int rows = 0;
        if (CollectionUtils.isNotEmpty(shares)) {
            List<String> ids = new LinkedList<String>();
            for (OwnerProductShare share : shares) {
                ids.add(share.getId());
            }

            String jpql = "DELETE FROM OwnerProductShare s " +
                "WHERE s.id in (:ids)";

            Query query = this.getEntityManager().createQuery(jpql);
            for (List<String> block : Iterables.partition(ids, getInBlockSize())) {
                rows += query.setParameter("ids", block).executeUpdate();
            }
        }
        return rows > 0;
    }
}
