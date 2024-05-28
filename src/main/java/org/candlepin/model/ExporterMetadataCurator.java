/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.NoResultException;



/**
 * ExportMetadataCurator
 */
@Singleton
public class ExporterMetadataCurator extends AbstractHibernateCurator<ExporterMetadata> {

    @Inject
    public ExporterMetadataCurator() {
        super(ExporterMetadata.class);
    }

    public ExporterMetadata getByType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }

        String query = "SELECT em FROM ExporterMetadata em WHERE em.type = :type";

        try {
            return this.getEntityManager()
                .createQuery(query, ExporterMetadata.class)
                .setParameter("type", type)
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    public ExporterMetadata getByTypeAndOwner(String type, Owner owner) {
        if (type == null || type.isBlank() || owner == null) {
            return null;
        }

        String query = "SELECT em FROM ExporterMetadata em " +
            "WHERE em.type = :type AND em.owner.id = :owner_id";

        try {
            return this.getEntityManager()
                .createQuery(query, ExporterMetadata.class)
                .setParameter("type", type)
                .setParameter("owner_id", owner.getId())
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

}
