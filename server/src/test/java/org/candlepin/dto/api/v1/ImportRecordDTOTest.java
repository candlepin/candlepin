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
 * Test suite for the ImportRecordDTO class
 */
public class ImportRecordDTOTest extends AbstractDTOTest<ImportRecordDTO> {

    protected Map<String, Object> values;

    public ImportRecordDTOTest() {
        super(ImportRecordDTO.class);

        ConsumerTypeDTO type = new ConsumerTypeDTO();
        type.setId("type_id");
        type.setLabel("type_label");
        type.setManifest(true);

        ImportUpstreamConsumerDTO consumer = new ImportUpstreamConsumerDTO();
        consumer.setId("test-id");
        consumer.setUuid("test-uuid");
        consumer.setName("test-name");
        consumer.setApiUrl("test-api-url");
        consumer.setWebUrl("test-web-url");
        consumer.setConsumerType(type);
        consumer.setOwnerId("test-owner-id");
        consumer.setContentAccessMode("test-content-access-mode");
        consumer.setCreated(new Date());
        consumer.setUpdated(new Date());

        this.values = new HashMap<>();
        this.values.put("Id", "test-id");
        this.values.put("Status", "test-status");
        this.values.put("StatusMessage", "test-status-msg");
        this.values.put("FileName", "test-filename");
        this.values.put("GeneratedBy", "test-source");
        this.values.put("GeneratedDate", new Date());
        this.values.put("UpstreamConsumer", consumer);
        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());
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
