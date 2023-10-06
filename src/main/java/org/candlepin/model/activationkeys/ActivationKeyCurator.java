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
package org.candlepin.model.activationkeys;

import org.candlepin.model.AbstractHibernateCurator;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Owner;

import com.google.inject.persist.Transactional;

import org.apache.commons.collections4.CollectionUtils;
import org.hibernate.annotations.QueryHints;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;



@Singleton
public class ActivationKeyCurator extends AbstractHibernateCurator<ActivationKey> {
    private static final Logger log = LoggerFactory.getLogger(ActivationKeyCurator.class);

    public ActivationKeyCurator() {
        super(ActivationKey.class);
    }

    public CandlepinQuery<ActivationKey> listByOwner(Owner owner, String keyName) {
        DetachedCriteria criteria = DetachedCriteria.forClass(ActivationKey.class)
            .add(Restrictions.eq("owner", owner));

        if (keyName != null) {
            criteria.add(Restrictions.eq("name", keyName));
        }

        return this.cpQueryFactory.buildQuery(this.currentSession(), criteria);
    }

    public CandlepinQuery<ActivationKey> listByOwner(Owner owner) {
        return this.listByOwner(owner, null);
    }

    @Transactional
    public ActivationKey update(ActivationKey key) {
        // Why is a method named "update" calling into "save" which is a synonym for "create" rather than
        // our update verb "merge" ????

        save(key);
        return key;
    }

    @Transactional
    public ActivationKey getByKeyName(Owner owner, String name) {
        // Impl note:
        // The usage of "uniqueResult" here is valid as long as we maintain the unique index on the
        // (owner, name) tuple. At the time of writing this is present in the table definition, but
        // could break things if removed.

        return (ActivationKey) this.currentSession().createCriteria(ActivationKey.class)
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.eq("name", name))
            .uniqueResult();
    }

    public List<ActivationKey> findByKeyNames(String ownerKey, Collection<String> keyNames) {
        List<ActivationKey> foundActivationKeys = new ArrayList<>();
        if (ownerKey != null && !CollectionUtils.isEmpty(keyNames)) {
            String hql = "SELECT key FROM ActivationKey key JOIN FETCH key.owner" +
                " WHERE key.owner.key = :owner_key AND key.name IN (:keys)";

            TypedQuery<ActivationKey> query = this.getEntityManager()
                .createQuery(hql, ActivationKey.class)
                .setParameter("owner_key", ownerKey);

            for (List<String> keyNameBlock : this.partition(keyNames)) {
                foundActivationKeys.addAll(query.setParameter("keys", keyNameBlock)
                    .getResultList());
            }
        }

        return foundActivationKeys;
    }

    /**
     * Removes the product references from any activation keys in the given organization.
     *
     * @param owner
     *  the owner/organization in which to remove product references from activation keys
     *
     * @param productIds
     *  a collection of IDs representing products to remove from activation keys within the org
     *
     * @return
     *  the number of activation key product references removed as a result of this operation
     */
    public int removeActivationKeyProductReferences(Owner owner, Collection<String> productIds) {
        EntityManager entityManager = this.getEntityManager();

        String jpql = "SELECT DISTINCT key.id FROM ActivationKey key WHERE key.ownerId = :owner_id";
        List<String> keyIds = entityManager.createQuery(jpql, String.class)
            .setParameter("owner_id", owner.getId())
            .getResultList();

        int count = 0;

        if (keyIds != null && !keyIds.isEmpty()) {
            // Delete the entries
            // Impl note: at the time of writing, JPA doesn't support doing this operation without
            // interacting with the objects directly. So, we're doing it with native SQL to avoid
            // even more work here.
            // Also note that MySQL/MariaDB doesn't like table aliases in a delete statement.
            String sql = "DELETE FROM cp_activation_key_products " +
                "WHERE key_id IN (:key_ids) AND product_id IN (:product_ids)";

            Query query = entityManager.createNativeQuery(sql)
                .setHint(QueryHints.NATIVE_SPACES, ActivationKey.class.getName());

            int blockSize = Math.min(this.getQueryParameterLimit() / 2, this.getInBlockSize() / 2);
            Iterable<List<String>> kidBlocks = this.partition(keyIds, blockSize);
            Iterable<List<String>> pidBlocks = this.partition(productIds, blockSize);

            for (List<String> kidBlock : kidBlocks) {
                query.setParameter("key_ids", kidBlock);

                for (List<String> pidBlock : pidBlocks) {
                    count += query.setParameter("product_ids", pidBlock)
                        .executeUpdate();
                }
            }
        }

        log.debug("{} activation-key product reference(s) removed", count);
        return count;
    }

}
