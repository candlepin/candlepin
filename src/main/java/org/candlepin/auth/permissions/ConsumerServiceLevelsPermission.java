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
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;

/**
 * Permission allowing consumers to view their owner's service levels.
 *
 * NOTE: consumer principals do not get full read access to their owner.
 */
public class ConsumerServiceLevelsPermission extends TypedPermission<Owner> {

    private Consumer consumer;

    public ConsumerServiceLevelsPermission(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public Class<Owner> getTargetType() {
        return Owner.class;
    }

    @Override
    public boolean canAccessTarget(Owner target, Access action) {
        return target.getKey().equals(consumer.getOwner().getKey()) &&
            action.equals(Access.READ_SERVICE_LEVELS);
    }
}
