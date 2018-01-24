/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.AbstractDTOTest;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Test suite for the EventDTO class
 */
public class EventDTOTest extends AbstractDTOTest<EventDTO> {

    protected Map<String, Object> values;

    public EventDTOTest() {
        super(EventDTO.class);

        this.values = new HashMap<String, Object>();
        EventDTO.PrincipalDataDTO principalData = new EventDTO.PrincipalDataDTO(
            "principal-type", "principal-name");
        this.values.put("Id", "id");
        this.values.put("TargetName", "target-name");
        this.values.put("PrincipalStore", "principal-store");
        this.values.put("Principal", principalData);
        this.values.put("Timestamp", new Date());
        this.values.put("EntityId", "entity-id");
        this.values.put("OwnerId", "owner-id");
        this.values.put("ConsumerId", "consumer-id");
        this.values.put("ReferenceId", "reference-id");
        this.values.put("EventData", "event-data");
        this.values.put("MessageText", "message-text");
        this.values.put("Type", "CREATED");
        this.values.put("Target", "POOL");
        this.values.put("ReferenceType", "POOL");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getInputValueForMutator(String field) {
        return this.values.get(field);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getOutputValueForAccessor(String field, Object input) {
        // Nothing to do here
        return input;
    }
}
