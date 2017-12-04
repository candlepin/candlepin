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

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.model.AbstractHibernateCurator;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Owner;

import com.google.inject.persist.Transactional;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

/**
 * SubscriptionTokenCurator
 */
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
        save(key);
        return key;
    }

    @Transactional
    public ActivationKey lookupForOwner(String keyName, Owner owner) {
        return (ActivationKey) currentSession().createCriteria(ActivationKey.class)
            .add(Restrictions.eq("name", keyName)).add(Restrictions.eq("owner", owner))
            .uniqueResult();
    }

    // TODO:
    // Move this method to the ActivationKeyResource. The curator should not be returning resource-level
    // exceptions
    public ActivationKey verifyAndLookupKey(String activationKeyId) {
        ActivationKey key = this.secureFind(activationKeyId);

        if (key == null) {
            throw new BadRequestException(
                i18n.tr("ActivationKey with id {0} could not be found.",
                    activationKeyId));
        }
        return key;
    }
}
