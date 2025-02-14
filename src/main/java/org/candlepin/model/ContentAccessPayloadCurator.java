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

import javax.inject.Singleton;
import javax.persistence.NoResultException;

/**
 * Curator responsible for datastore operation for the {@link ContentAccessPayload} entity.
 */
@Singleton
public class ContentAccessPayloadCurator extends AbstractHibernateCurator<ContentAccessPayload> {

    public ContentAccessPayloadCurator() {
        super(ContentAccessPayload.class);
    }

    /**
     * Retrieves a {@link ContentAccessPayload} based on a provided {@link Owner} ID and payload key.
     *
     * @param ownerId
     *  the ID of the owner which owns the content access payload
     *
     * @param payloadKey
     *  the payload of key of the content access payload
     *
     * @return the content access payload that has the provided payload key and belongs to the provided owner,
     *  or null if the payload cannot be found
     */
    public ContentAccessPayload getContentAccessPayload(String ownerId, String payloadKey) {
        if (ownerId == null || ownerId.isBlank()) {
            return null;
        }

        if (payloadKey == null || payloadKey.isBlank()) {
            return null;
        }

        String jpql = "SELECT cap " +
            "FROM ContentAccessPayload cap " +
            "WHERE cap.ownerId = :ownerId AND cap.payloadKey = :payloadKey";

        try {
            return getEntityManager()
                .createQuery(jpql, ContentAccessPayload.class)
                .setParameter("ownerId", ownerId)
                .setParameter("payloadKey", payloadKey)
                .getSingleResult();
        }
        catch (NoResultException e) {
            // Intentionally left empty
        }

        return null;
    }

}
