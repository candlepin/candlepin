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
package org.candlepin.auth;

import org.candlepin.auth.permissions.AnonymousCloudConsumerPermission;
import org.candlepin.model.AnonymousCloudConsumer;

import java.util.Objects;



/**
 * AnonymousCloudConsumerPrincipal is a principal representing a trusted {@link AnonymousCloudConsumer}
 */
public class AnonymousCloudConsumerPrincipal extends Principal {

    private AnonymousCloudConsumer consumer;

    public AnonymousCloudConsumerPrincipal(AnonymousCloudConsumer consumer) {
        this.consumer = Objects.requireNonNull(consumer);

        addPermission(new AnonymousCloudConsumerPermission(consumer));
    }

    /**
     * @return the {@link AnonymousCloudConsumer} for this principal
     */
    public AnonymousCloudConsumer getAnonymousCloudConsumer() {
        return this.consumer;
    }

    @Override
    public String getType() {
        return "anonymouscloudconsumer";
    }

    @Override
    public boolean hasFullAccess() {
        return false;
    }

    @Override
    public String getName() {
        return this.consumer.getUuid();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj != null &&
            obj instanceof AnonymousCloudConsumerPrincipal other) {
            return this.consumer.getUuid().equals(other.consumer.getUuid());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.consumer.getUuid());
    }

    @Override
    public AuthenticationMethod getAuthenticationMethod() {
        return AuthenticationMethod.ANONYMOUS_CLOUD;
    }

}
