/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.candlepin.dto.AbstractDTOTest;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * Test suite for the HypervisorUpdateResultDTO class
 */
public class HypervisorUpdateResultDTOTest extends AbstractDTOTest<HypervisorUpdateResultDTO> {

    protected Map<String, Object> values;

    public HypervisorUpdateResultDTOTest() {
        super(HypervisorUpdateResultDTO.class);

        this.values = new HashMap<>();

        Set<HypervisorConsumerDTO> created = new HashSet<>();
        Set<HypervisorConsumerDTO> updated = new HashSet<>();
        Set<HypervisorConsumerDTO> unchanged = new HashSet<>();
        Set<String> failed = new HashSet<>();
        for (int i = 0; i < 3; ++i) {
            HypervisorConsumerDTO consumerCreated = new HypervisorConsumerDTO();
            HypervisorConsumerDTO consumerUpdated = new HypervisorConsumerDTO();
            HypervisorConsumerDTO consumerUnchanged = new HypervisorConsumerDTO();

            consumerCreated.setName("created_" + i);
            consumerUpdated.setName("updated_" + i);
            consumerUnchanged.setName("unchanged_" + i);

            consumerCreated.setUuid("created_uuid_" + i);
            consumerUpdated.setUuid("updated_uuid_" + i);
            consumerUnchanged.setUuid("unchanged_uuid_" + i);

            HypervisorConsumerDTO.OwnerDTO ofCreated = new HypervisorConsumerDTO.OwnerDTO();
            HypervisorConsumerDTO.OwnerDTO ofUpdated = new HypervisorConsumerDTO.OwnerDTO();
            HypervisorConsumerDTO.OwnerDTO ofUnchanged = new HypervisorConsumerDTO.OwnerDTO();

            ofCreated.setKey("created_key_" + i);
            ofUpdated.setKey("updated_key_" + i);
            ofUnchanged.setKey("unchanged_key_" + i);

            consumerCreated.setOwner(ofCreated);
            consumerUpdated.setOwner(ofUpdated);
            consumerUnchanged.setOwner(ofUnchanged);

            created.add(consumerCreated);
            updated.add(consumerUpdated);
            unchanged.add(consumerUnchanged);
            failed.add("failure reason: " + i);
        }

        this.values.put("Created", created);
        this.values.put("Updated", updated);
        this.values.put("Unchanged", unchanged);
        this.values.put("FailedUpdate", failed);
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

    @Test
    public void testAddCreatedWithAbsentCreated() {
        HypervisorUpdateResultDTO dto = new HypervisorUpdateResultDTO();

        HypervisorConsumerDTO created = new HypervisorConsumerDTO();
        created.setUuid("test-created-uuid-id-1");
        created.setName("test-created-name-1");
        HypervisorConsumerDTO.OwnerDTO owner = new HypervisorConsumerDTO.OwnerDTO();
        owner.setKey("test-key-1");
        created.setOwner(owner);
        assertTrue(dto.addCreated(created));
    }

    @Test
    public void testAddCreatedWithPresentCreated() {
        HypervisorUpdateResultDTO dto = new HypervisorUpdateResultDTO();

        HypervisorConsumerDTO created = new HypervisorConsumerDTO();
        created.setUuid("test-created-uuid-id-2");
        created.setName("test-created-name-2");
        HypervisorConsumerDTO.OwnerDTO owner = new HypervisorConsumerDTO.OwnerDTO();
        owner.setKey("test-key-2");
        created.setOwner(owner);
        assertTrue(dto.addCreated(created));

        HypervisorConsumerDTO created2 = new HypervisorConsumerDTO();
        created2.setUuid("test-created-uuid-id-2");
        created2.setName("test-created-name-2");
        HypervisorConsumerDTO.OwnerDTO owner2 = new HypervisorConsumerDTO.OwnerDTO();
        owner2.setKey("test-key-2");
        created2.setOwner(owner2);
        assertFalse(dto.addCreated(created2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddCreatedWithNullInput() {
        HypervisorUpdateResultDTO dto = new HypervisorUpdateResultDTO();
        dto.addCreated(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddCreatedWithEmptyUuid() {
        HypervisorUpdateResultDTO dto = new HypervisorUpdateResultDTO();

        HypervisorConsumerDTO created = new HypervisorConsumerDTO();
        created.setUuid("");
        created.setName("test-created-name-3");
        HypervisorConsumerDTO.OwnerDTO owner3 = new HypervisorConsumerDTO.OwnerDTO();
        owner3.setKey("test-key-3");
        created.setOwner(owner3);
        dto.addCreated(created);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddCreatedWithEmptyName() {
        HypervisorUpdateResultDTO dto = new HypervisorUpdateResultDTO();

        HypervisorConsumerDTO created = new HypervisorConsumerDTO();
        created.setUuid("test-created-uuid-id-4");
        created.setName("");
        HypervisorConsumerDTO.OwnerDTO owner4 = new HypervisorConsumerDTO.OwnerDTO();
        owner4.setKey("test-key-4");
        created.setOwner(owner4);
        dto.addCreated(created);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddCreatedWithNullOwner() {
        HypervisorUpdateResultDTO dto = new HypervisorUpdateResultDTO();

        HypervisorConsumerDTO created = new HypervisorConsumerDTO();
        created.setUuid("test-created-uuid-id-5");
        created.setName("test-created-name-5");
        created.setOwner(null);
        dto.addCreated(created);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddCreatedWithNullOwnerKey() {
        HypervisorUpdateResultDTO dto = new HypervisorUpdateResultDTO();

        HypervisorConsumerDTO created = new HypervisorConsumerDTO();
        created.setUuid("test-created-uuid-id-5");
        created.setName("test-created-name-5");
        created.setOwner(new HypervisorConsumerDTO.OwnerDTO());
        dto.addCreated(created);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddCreatedWithEmptyOwnerKey() {
        HypervisorUpdateResultDTO dto = new HypervisorUpdateResultDTO();

        HypervisorConsumerDTO created = new HypervisorConsumerDTO();
        created.setUuid("test-created-uuid-id-5");
        created.setName("test-created-name-5");
        HypervisorConsumerDTO.OwnerDTO owner = new HypervisorConsumerDTO.OwnerDTO();
        owner.setKey("");
        created.setOwner(owner);
        dto.addCreated(created);
    }
}
