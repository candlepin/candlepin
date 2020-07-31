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

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Component;


/**
 * Curator for {@link ImportRecord}s.
 */
@Component
public class ImportRecordCurator extends AbstractHibernateCurator<ImportRecord> {

    public ImportRecordCurator() {
        super(ImportRecord.class);
    }

    /**
     * Returns the {@link ImportRecord}s for this owner in reverse chronological
     * order.
     *
     * @param owner the {@link Owner}
     * @return the import records
     */
    public CandlepinQuery<ImportRecord> findRecords(Owner owner) {
        DetachedCriteria criteria = DetachedCriteria.forClass(ImportRecord.class)
            .add(Restrictions.eq("owner", owner))
            .addOrder(Order.desc("created"));

        return this.cpQueryFactory.<ImportRecord>buildQuery(this.currentSession(), criteria);
    }
}
