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

package org.candlepin.gutterball.report;

import org.candlepin.gutterball.model.snapshot.Compliance;
import org.candlepin.gutterball.report.dto.ConsumerStatusComplianceDto;

import java.util.Iterator;

/**
 * Represents the default ConsumerStatusReport results. An instance of this class wraps an iterator of
 * Compliance records that come from the database, so that each Compliance record can be transformed
 * into a minimal DTO that gets sent back to the client.
 */
public class ConsumerStatusReportDefaultResult extends
    ComplianceTransformerIterator<ConsumerStatusComplianceDto> {

    public ConsumerStatusReportDefaultResult(Iterator<Compliance> dbIterator) {
        super(dbIterator);
    }

    @Override
    ConsumerStatusComplianceDto convertDbObject(Compliance compliance) {
        return new ConsumerStatusComplianceDto(compliance);
    }

}
