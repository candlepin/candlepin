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

import java.util.List;

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.AbstractHibernateCurator;
import org.candlepin.model.Owner;
import org.hibernate.criterion.Restrictions;

import com.google.inject.persist.Transactional;

/**
 * SubscriptionTokenCurator
 */
public class ActivationKeyCurator extends AbstractHibernateCurator<ActivationKey> {

    protected ActivationKeyCurator() {
        super(ActivationKey.class);
    }

    public List<ActivationKey> listByOwner(Owner owner) {
        return (List<ActivationKey>) currentSession().createCriteria(ActivationKey.class)
        .add(Restrictions.eq("owner", owner)).list();
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
