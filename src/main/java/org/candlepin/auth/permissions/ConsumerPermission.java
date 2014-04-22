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
package org.candlepin.auth.permissions;

import org.candlepin.auth.Access;
import org.candlepin.auth.SubResource;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;

/**
 *
 */
public class ConsumerPermission extends TypedPermission<Consumer> {

    private final Consumer consumer;

    public ConsumerPermission(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public Class<Consumer> getTargetType() {
        return Consumer.class;
    }

    @Override
    public boolean canAccessTarget(Consumer target, SubResource subResource,
        Access required) {
        return this.consumer.getUuid().equals(target.getUuid());
    }

    @Override
    public Criterion getCriteriaRestrictions(Class entityClass) {
        if (entityClass.equals(Consumer.class)) {
            return Restrictions.idEq(consumer.getId());
        }
        return null;
    }

    @Override
    public Owner getOwner() {
        return consumer.getOwner();
    }

    public Consumer getConsumer() {
        return consumer;
    }

}
