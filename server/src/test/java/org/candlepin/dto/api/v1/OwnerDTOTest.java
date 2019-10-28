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
 * Test suite for the OwnerDTO class
 */
public class OwnerDTOTest extends AbstractDTOTest<OwnerDTO> {

    protected Map<String, Object> values;

    public OwnerDTOTest() {
        super(OwnerDTO.class);

        OwnerDTO parent = new OwnerDTO();
        parent.setId("owner_id");
        parent.setKey("owner_key");
        parent.setDisplayName("owner_name");
        parent.setContentPrefix("content_prefix");
        parent.setDefaultServiceLevel("service_level");
        parent.setLogLevel("log_level");
        parent.setAutobindDisabled(true);
        parent.setAutobindHypervisorDisabled(true);
        parent.setContentAccessMode("content_access_mode");
        parent.setContentAccessModeList("content_access_mode_list");

        UpstreamConsumerDTO consumer = new UpstreamConsumerDTO();
        consumer.setId("consumer_id");
        consumer.setUuid("consumer_uuid");
        consumer.setName("consumer_name");
        consumer.setApiUrl("http://www.url.com");
        consumer.setWebUrl("http://www.url.com");
        consumer.setOwnerId("owner_id");

        this.values = new HashMap<>();
        this.values.put("Id", "test-id");
        this.values.put("Key", "test-key");
        this.values.put("DisplayName", "test-name");
        this.values.put("ParentOwner", parent);
        this.values.put("ContentPrefix", "test-prefix");
        this.values.put("DefaultServiceLevel", "test-service-level");
        this.values.put("UpstreamConsumer", consumer);
        this.values.put("LogLevel", "test-log-level");
        this.values.put("AutobindDisabled", true);
        this.values.put("AutobindHypervisorDisabled", true);
        this.values.put("ContentAccessMode", "test-access-mode");
        this.values.put("ContentAccessModeList", "test-access-mode-list");
        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());
        this.values.put("LastRefreshed", new Date());
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
