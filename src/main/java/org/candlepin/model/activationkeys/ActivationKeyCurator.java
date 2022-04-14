/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
import java.util.Collections;
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
        if (ownerKey == null || CollectionUtils.isEmpty(keyNames)) {
            return Collections.emptyList();
        }

        String hql = "SELECT key FROM ActivationKey key JOIN FETCH key.owner" +
            " WHERE key.owner.key = :owner_key AND key.name IN (:keys)";

        TypedQuery<ActivationKey> query = this.getEntityManager()
            .createQuery(hql, ActivationKey.class)
            .setParameter("owner_key", ownerKey);

        List<ActivationKey> foundActivationKeys = new ArrayList<>();
        for (List<String> keyNameBlock : this.partition(keyNames)) {
            foundActivationKeys.addAll(query
                .setParameter("keys", keyNameBlock)
                .getResultList());
        }

        return foundActivationKeys;
    }

}
