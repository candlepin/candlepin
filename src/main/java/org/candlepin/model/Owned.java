/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.model;

/**
 * Represents an entity that belongs to an {@link Owner}.
 */
public interface Owned {

    /**
     * @return the ID of the {@link Owner} that owns this entity
     */
    String getOwnerId();

    /**
     * @return the key of the {@link Owner} that owns this entity
     */
    String getOwnerKey();

    /**
     * @return the anonymous state of the {@link Owner} that owns this entity
     */
    boolean isOwnerAnonymous();
}
