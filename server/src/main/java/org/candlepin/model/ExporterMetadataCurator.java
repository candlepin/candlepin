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

import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;



/**
 * ExportMetadataCurator
 */
@Component
public class ExporterMetadataCurator extends AbstractHibernateCurator<ExporterMetadata> {

    @Autowired
    public ExporterMetadataCurator() {
        super(ExporterMetadata.class);
    }

    public ExporterMetadata getByType(String type) {
        return (ExporterMetadata) this.currentSession().createCriteria(ExporterMetadata.class)
            .add(Restrictions.eq("type", type))
            .uniqueResult();
    }

    public ExporterMetadata getByTypeAndOwner(String type, Owner owner) {
        return (ExporterMetadata) this.currentSession().createCriteria(ExporterMetadata.class)
            .add(Restrictions.eq("type", type))
            .add(Restrictions.eq("owner", owner))
            .uniqueResult();
    }

}
