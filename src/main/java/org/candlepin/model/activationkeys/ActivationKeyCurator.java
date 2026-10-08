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
package org.candlepin.model.activationkeys;

import org.candlepin.model.AbstractHibernateCurator;
import org.candlepin.model.Owner;

import org.apache.commons.collections4.CollectionUtils;
import org.hibernate.query.NativeQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;


@Singleton
public class ActivationKeyCurator extends AbstractHibernateCurator<ActivationKey> {
    private static final Logger log = LoggerFactory.getLogger(ActivationKeyCurator.class);

    public ActivationKeyCurator() {
        super(ActivationKey.class);
    }

    /**
     * Retrieves the activation keys for the owner. If the provided key name is non-null and non-blank, then
     * activation keys will be filtered based on that key name. If the provided {@link Owner} is null, then an
     * empty list is returned. This method does not return null.
     *
     * @param owner
     *  the owner of the activation keys that should be returned
     *
     * @param keyName
     *  an optional parameter that will filter the activation keys based on the name
     *
     * @return the activation keys for the owner
     */
    public List<ActivationKey> listByOwner(Owner owner, String keyName) {
        if (owner == null) {
            return Collections.emptyList();
        }

        boolean hasKeyName = keyName != null && !keyName.isBlank();
        String nameCondition = "";
        if (hasKeyName) {
            nameCondition = " AND a.name = :name";
        }

        // Eagerly fetch collections to avoid an N + 1 problem during DTO translation

        String jpql = "SELECT DISTINCT a " +
            "FROM ActivationKey a " +
            "LEFT JOIN FETCH a.productIds " +
            "LEFT JOIN FETCH a.contentOverrides " +
            "LEFT JOIN FETCH a.pools " +
            "LEFT JOIN FETCH a.addOns " +
            "WHERE a.owner = :owner" + nameCondition;

        TypedQuery<ActivationKey> query = this.getEntityManager()
            .createQuery(jpql, ActivationKey.class)
            .setParameter("owner", owner);

        if (hasKeyName) {
            query.setParameter("name", keyName);
        }

        return query.getResultList();
    }

    public List<ActivationKey> listByOwner(Owner owner) {
        return this.listByOwner(owner, null);
    }

    public ActivationKey getByKeyName(Owner owner, String name) {
        // Impl note:
        // The usage of "getSingleResult" here is valid as long as we maintain the unique index on the
        // (owner, name) tuple. At the time of writing this is present in the table definition, but
        // could break things if removed.

        String jpql = "SELECT a FROM ActivationKey a WHERE a.owner = :owner AND a.name = :name";

        try {
            return this.getEntityManager().createQuery(jpql, ActivationKey.class)
                .setParameter("owner", owner)
                .setParameter("name", name)
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
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
                .unwrap(NativeQuery.class)
                .addSynchronizedEntityClass(ActivationKey.class)
                .addSynchronizedQuerySpace("cp_activation_key_products");

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

    /**
     * Deletes all the {@link ActivationKeyPool}s for the provided owner.
     *
     * @param ownerKey
     *  the key to the owner to delete {@link ActivationKeyPool}s for
     *
     * @throws IllegalArgumentException
     *  if the provided owner key is null or blank
     *
     * @return the number of deleted {@link ActivationKeyPool}s
     */
    public int removeActivationKeyPools(String ownerKey) {
        if (ownerKey == null || ownerKey.isBlank()) {
            throw new IllegalArgumentException("owner key is null or blank");
        }

        String akPoolIdJpql = "SELECT ap.id FROM ActivationKeyPool ap " +
            "JOIN Pool p ON p.id = ap.pool.id " +
            "WHERE p.owner.key = :owner_key";

        List<String> ids = this.getEntityManager()
            .createQuery(akPoolIdJpql, String.class)
            .setParameter("owner_key", ownerKey)
            .getResultList();

        String deleteJpql = "DELETE FROM ActivationKeyPool akp " +
            "WHERE akp.id IN (:ids)";

        int deleted = 0;
        for (Collection<String> block : this.partition(ids)) {
            deleted += this.getEntityManager()
                .createQuery(deleteJpql)
                .setParameter("ids", block)
                .executeUpdate();
        }

        return deleted;
    }
}
