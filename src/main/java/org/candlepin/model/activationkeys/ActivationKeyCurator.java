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

import org.apache.commons.collections.CollectionUtils;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Singleton;
import javax.persistence.TypedQuery;


@Singleton
public class ActivationKeyCurator extends AbstractHibernateCurator<ActivationKey> {

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
                foundActivationKeys.addAll(query
                    .setParameter("keys", keyNameBlock)
                    .getResultList());
            }
        }
        return foundActivationKeys;
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
    @Transactional
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
            deleted += this.currentSession()
                .createQuery(deleteJpql)
                .setParameter("ids", block)
                .executeUpdate();
        }

        return deleted;
    }
}
