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
package org.candlepin.auth.permissions;

import org.candlepin.auth.Access;
import org.candlepin.auth.SubResource;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousCloudConsumer_;
import org.candlepin.model.Owner;

import java.util.Objects;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;



/**
 * A permission granting access to a {@link AnonymousCloudConsumer} entity.
 */
public class AnonymousCloudConsumerPermission extends TypedPermission<AnonymousCloudConsumer> {

    private AnonymousCloudConsumer consumer;

    public AnonymousCloudConsumerPermission(AnonymousCloudConsumer consumer) {
        this.consumer = Objects.requireNonNull(consumer);
    }

    @Override
    public <T> Predicate getQueryRestriction(Class<T> entityClass,
        CriteriaBuilder builder, From<?, T> path) {
        if (AnonymousCloudConsumer.class.equals(entityClass)) {
            return builder.equal(((From<?, AnonymousCloudConsumer>) path).get(AnonymousCloudConsumer_.id),
                this.getAnonymousCloudConsumer().getId());
        }

        return null;
    }

    @Override
    public Owner getOwner() {
        // Anonymous cloud consumers have no owner
        return null;
    }

    @Override
    public Class<AnonymousCloudConsumer> getTargetType() {
        return AnonymousCloudConsumer.class;
    }

    @Override
    public boolean canAccessTarget(AnonymousCloudConsumer target,
        SubResource subResource, Access action) {
        if (target == null) {
            return false;
        }

        return this.consumer.getUuid().equals(target.getUuid());
    }

    /**
     * @return the anonymous cloud consumer that this permission grants access to
     */
    public AnonymousCloudConsumer getAnonymousCloudConsumer() {
        return this.consumer;
    }
}
