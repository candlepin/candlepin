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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;



@Singleton
public class ContentAccessPayloadCurator {
    private static final Logger log = LoggerFactory.getLogger(ContentAccessPayloadCurator.class);

    private final Provider<EntityManager> entityManagerProvider;


    @Inject
    public ContentAccessPayloadCurator(Provider<EntityManager> entityManagerProvider) {
        this.entityManagerProvider = Objects.requireNonNull(entityManagerProvider);
    }

    private EntityManager getEntityManager() {
        return this.entityManagerProvider.get();
    }

    public ContentAccessPayload getContentAccessPayload(String ownerId, String payloadKey) {
        String jpql = "SELECT cap FROM ContentAccessPayload cap " +
            "WHERE cap.ownerId = :ownerId " +
            "  AND cap.payloadKey = :payloadKey";

        try {
            return this.getEntityManager()
                .createQuery(jpql, ContentAccessPayload.class)
                .setParameter("ownerId", ownerId)
                .setParameter("payloadKey", payloadKey)
                .getSingleResult();
        }
        catch (NoResultException exception) {
            // Intentionally left empty
        }

        return null;
    }

    public ContentAccessPayload persist(ContentAccessPayload entity) {
        if (entity == null) {
            return null;
        }

        this.getEntityManager()
            .persist(entity);

        return entity;
    }

    public ContentAccessPayload merge(ContentAccessPayload entity) {
        if (entity == null) {
            return null;
        }

        return this.getEntityManager()
            .merge(entity);
    }
}
