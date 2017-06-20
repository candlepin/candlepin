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
package org.candlepin.resource;

import static org.junit.Assert.*;

import org.candlepin.dto.api.v1.ConsumerTypeDTO;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.model.ConsumerType;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;



/**
 * ConsumerTypeResourceTest
 */
public class ConsumerTypeResourceTest extends DatabaseTestFixture {
    @Inject private ConsumerTypeResource consumerTypeResource;

    private ConsumerType testType;

    @Before
    public void setup() {
        this.testType = new ConsumerType();
        this.testType.setLabel("test_type-" + System.currentTimeMillis());
        this.testType.setManifest(true);

        this.testType = this.consumerTypeCurator.create(this.testType);

        this.consumerTypeCurator.flush();
        this.consumerTypeCurator.clear();
    }

    @Test
    public void testRetrieveType() {
        ConsumerTypeDTO output = this.consumerTypeResource.getConsumerType(this.testType.getId());

        assertNotNull(output);
        assertEquals(this.testType.getLabel(), output.getLabel());
        assertEquals(this.testType.isManifest(), output.isManifest());
    }

    @Test(expected = NotFoundException.class)
    public void testRetrieveTypeWithBadId() {
        ConsumerTypeDTO output = this.consumerTypeResource.getConsumerType("some bad id");
    }

    @Test(expected = NotFoundException.class)
    public void testRetrieveTypeWithNullId() {
        ConsumerTypeDTO output = this.consumerTypeResource.getConsumerType(null);
    }

    @Test
    public void testCreateType() {
        String label = "test_label";
        ConsumerType existing = this.consumerTypeCurator.lookupByLabel(label);

        // Verify it doesn't exist already
        assertNull(existing);

        ConsumerTypeDTO dto = new ConsumerTypeDTO();
        dto.setLabel(label);
        dto.setManifest(true);

        // Create the type with our DTO
        ConsumerTypeDTO output = this.consumerTypeResource.create(dto);
        assertNotNull(output);
        assertEquals(dto.getLabel(), output.getLabel());
        assertEquals(dto.isManifest(), output.isManifest());

        // Flush & clear DB state
        this.consumerTypeCurator.flush();
        this.consumerTypeCurator.clear();

        // Ensure the type actually hit the DB
        existing = this.consumerTypeCurator.lookupByLabel(label);
        assertNotNull(existing);
        assertEquals(dto.getLabel(), existing.getLabel());
        assertEquals(dto.isManifest(), existing.isManifest());
    }

    @Test(expected = BadRequestException.class)
    public void testCreateTypeWithBadData() {
        ConsumerTypeDTO output = this.consumerTypeResource.create(new ConsumerTypeDTO());
    }

    @Test
    public void testUpdateTypeUpdatesLabelButNotManifest() {
        String label = "updated_label";
        boolean manifest = this.testType.isManifest();

        ConsumerTypeDTO dto = new ConsumerTypeDTO();
        dto.setId(this.testType.getId());
        dto.setLabel(label);

        // Update the type with our DTO
        ConsumerTypeDTO output = this.consumerTypeResource.update(dto);
        assertNotNull(output);
        assertEquals(dto.getLabel(), output.getLabel());
        assertEquals(manifest, output.isManifest());

        // Flush & clear DB state
        this.consumerTypeCurator.flush();
        this.consumerTypeCurator.clear();

        // Ensure the update actually hit the DB
        ConsumerType existing = this.consumerTypeCurator.lookupByLabel(label);
        assertNotNull(existing);
        assertEquals(dto.getLabel(), existing.getLabel());
        assertEquals(manifest, existing.isManifest());
    }

    @Test
    public void testUpdateTypeUpdatesManifestButNotLabel() {
        String label = this.testType.getLabel();
        boolean manifest = !this.testType.isManifest();

        ConsumerTypeDTO dto = new ConsumerTypeDTO();
        dto.setId(this.testType.getId());
        dto.setManifest(manifest);

        // Update the type with our DTO
        ConsumerTypeDTO output = this.consumerTypeResource.update(dto);
        assertNotNull(output);
        assertEquals(label, output.getLabel());
        assertEquals(dto.isManifest(), output.isManifest());

        // Flush & clear DB state
        this.consumerTypeCurator.flush();
        this.consumerTypeCurator.clear();

        // Ensure the update actually hit the DB
        ConsumerType existing = this.consumerTypeCurator.find(this.testType.getId());
        assertNotNull(existing);
        assertEquals(label, existing.getLabel());
        assertEquals(dto.isManifest(), existing.isManifest());
    }

    @Test
    public void testUpdateTypeUpdatesNothing() {
        String label = this.testType.getLabel();
        boolean manifest = this.testType.isManifest();

        ConsumerTypeDTO dto = new ConsumerTypeDTO();
        dto.setId(this.testType.getId());

        // Update the type with our DTO
        ConsumerTypeDTO output = this.consumerTypeResource.update(dto);
        assertNotNull(output);
        assertEquals(label, output.getLabel());
        assertEquals(manifest, output.isManifest());

        // Flush & clear DB state
        this.consumerTypeCurator.flush();
        this.consumerTypeCurator.clear();

        // Ensure the update changed nothing in the DB
        ConsumerType existing = this.consumerTypeCurator.find(this.testType.getId());
        assertNotNull(existing);
        assertEquals(label, existing.getLabel());
        assertEquals(manifest, existing.isManifest());
    }

    @Test(expected = NotFoundException.class)
    public void testUpdateTypeWithBadId() {
        ConsumerTypeDTO dto = new ConsumerTypeDTO();
        dto.setId("some bad id");

        ConsumerTypeDTO output = this.consumerTypeResource.update(dto);
    }

    @Test(expected = NotFoundException.class)
    public void testUpdateTypeWithNullId() {
        ConsumerTypeDTO dto = new ConsumerTypeDTO();
        dto.setId(null);

        ConsumerTypeDTO output = this.consumerTypeResource.update(dto);
    }

    @Test
    public void testDeleteType() {
        String id = this.testType.getId();
        assertNotNull(this.consumerTypeCurator.find(id));

        this.consumerTypeResource.deleteConsumerType(id);

        // Flush & clear DB state
        this.consumerTypeCurator.flush();
        this.consumerTypeCurator.clear();

        // Verify the type no longer exists
        assertNull(this.consumerTypeCurator.find(id));
    }

    @Test(expected = NotFoundException.class)
    public void testDeleteTypeWithBadId() {
        this.consumerTypeResource.deleteConsumerType("some bad id");
    }

    @Test(expected = NotFoundException.class)
    public void testDeleteTypeWithNullId() {
        this.consumerTypeResource.deleteConsumerType(null);
    }

}
