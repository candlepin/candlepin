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
package org.candlepin.model;

import com.google.inject.Inject;

import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

/**
 * ExportMetadataCurator
 */
public class ExporterMetadataCurator extends AbstractHibernateCurator<ExporterMetadata> {

    @Inject
    public ExporterMetadataCurator() {
        super(ExporterMetadata.class);
    }

    public ExporterMetadata lookupByType(String type) {
        Criteria query = currentSession().createCriteria(ExporterMetadata.class);
        query.add(Restrictions.eq("type", type));
        return (ExporterMetadata) query.uniqueResult();
    }

    public ExporterMetadata lookupByTypeAndOwner(String type, Owner owner) {
        Criteria query = currentSession().createCriteria(ExporterMetadata.class);
        query.add(Restrictions.eq("type", type));
        query.add(Restrictions.eq("owner", owner));
        return (ExporterMetadata) query.uniqueResult();
    }

}
