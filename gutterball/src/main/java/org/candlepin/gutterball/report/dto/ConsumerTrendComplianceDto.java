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

package org.candlepin.gutterball.report.dto;

import org.candlepin.gutterball.model.snapshot.Compliance;
import org.candlepin.gutterball.model.snapshot.ComplianceReason;
import org.candlepin.gutterball.model.snapshot.ComplianceStatus;
import org.candlepin.gutterball.model.snapshot.Consumer;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The default data that will be returned by the ConsumerTrendReport. This is purly a DTO that
 * limits the amount of processing that Jackson has to do when serializing {@link Compliance} objects
 * to include in the response.
 */
public class ConsumerTrendComplianceDto extends HashMap<String, Object> {

    public ConsumerTrendComplianceDto(Compliance compliance) {
        ComplianceStatus status = compliance.getStatus();
        Consumer consumer = compliance.getConsumer();

        // TODO: Using maps instead of individual fields seems to be a little faster to serialize
        //       and allows us to keep the same result JSON structure as using the custom report.
        //       Should this object just contain a field for each property that we want to return.
        List<Map<String, Object>> reasonsData = new LinkedList<Map<String, Object>>();
        for (ComplianceReason cr : status.getReasons()) {
            HashMap<String, Object> reasonData = new HashMap<String, Object>();
            reasonData.put("message", cr.getMessage());
            reasonData.put("attributes", new HashMap<String, String>(cr.getAttributes()));
            reasonsData.add(reasonData);
        }

        HashMap<String, Object> statusData = new HashMap<String, Object>();
        statusData.put("status", status.getStatus());
        statusData.put("date", status.getDate());
        statusData.put("reasons", reasonsData);

        Map<String, Object> consumerData = new HashMap<String, Object>();
        consumerData.put("uuid", consumer.getUuid());
        consumerData.put("lastCheckin", consumer.getLastCheckin());

        put("status", statusData);
        put("consumer", consumerData);
    }

}
