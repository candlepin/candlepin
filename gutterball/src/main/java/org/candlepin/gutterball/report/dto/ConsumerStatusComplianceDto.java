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

import org.candlepin.gutterball.model.ConsumerState;
import org.candlepin.gutterball.model.snapshot.Compliance;
import org.candlepin.gutterball.model.snapshot.ComplianceStatus;
import org.candlepin.gutterball.model.snapshot.Consumer;
import org.candlepin.gutterball.model.snapshot.Owner;

import java.util.HashMap;
import java.util.Map;

/**
 * The default data that will be returned by the ConsumerStatusReport. This is purely a DTO that
 * limits the amount of processing that Jackson has to do when serializing {@link Compliance} objects
 * to include in the response.
 */
public class ConsumerStatusComplianceDto extends HashMap<String, Object> {

    public ConsumerStatusComplianceDto(Compliance snap) {
        // TODO: Using maps instead of individual fields seems to be a little faster to serialize
        //       and allows us to keep the same result JSON structure as using the custom report.
        //       Should this object just contain a field for each property that we want to return.
        Map<String, Object> ownerData = new HashMap<String, Object>();
        Consumer consumer = snap.getConsumer();

        Owner owner = consumer.getOwner();
        ownerData.put("key", owner.getKey());
        ownerData.put("displayName", owner.getDisplayName());

        Map<String, Object> consumerStateData = new HashMap<String, Object>();
        ConsumerState consumerState = consumer.getConsumerState();
        consumerStateData.put("created", consumerState.getCreated());
        consumerStateData.put("deleted", consumerState.getDeleted());

        Map<String, Object> consumerData = new HashMap<String, Object>();
        consumerData.put("uuid", consumer.getUuid());
        consumerData.put("name", consumer.getName());
        consumerData.put("lastCheckin", consumer.getLastCheckin());
        consumerData.put("owner", ownerData);
        // wrap in a new map to dereference hibernate in any way.
        consumerData.put("facts", new HashMap<String, String>(consumer.getFacts()));
        consumerData.put("consumerState", consumerStateData);


        Map<String, Object> statusData = new HashMap<String, Object>();
        ComplianceStatus status = snap.getStatus();
        statusData.put("status", status.getStatus());
        statusData.put("date", status.getDate());

        put("consumer", consumerData);
        put("status", statusData);
    }

}
