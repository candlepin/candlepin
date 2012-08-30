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
package org.candlepin.model.curator;

import com.google.inject.Inject;

import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.Owner;
import org.hibernate.criterion.DetachedCriteria;
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
        DetachedCriteria query = DetachedCriteria.forClass(ExporterMetadata.class);
        query.add(Restrictions.eq("type", type));
        return getByCriteria(query);
    }

    public ExporterMetadata lookupByTypeAndOwner(String type, Owner owner) {
        DetachedCriteria query = DetachedCriteria.forClass(ExporterMetadata.class);
        query.add(Restrictions.eq("type", type));
        query.add(Restrictions.eq("owner", owner));
        return getByCriteria(query);
    }

}
