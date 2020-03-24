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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.candlepin.dto.AbstractDTOTest;

import org.junit.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test suite for the ConsumerDTO class
 */
public class ConsumerDTOTest extends AbstractDTOTest<ConsumerDTO> {


    protected Map<String, Object> values;
    protected EnvironmentDTOTest environmentDTOTest = new EnvironmentDTOTest();
    protected ConsumerInstalledProductDTOTest cipDTOTest = new ConsumerInstalledProductDTOTest();
    protected CapabilityDTOTest capabilityDTOTest = new CapabilityDTOTest();
    protected HypervisorIdDTOTest hypervisorIdDTOTest = new HypervisorIdDTOTest();

    public ConsumerDTOTest() {
        super(ConsumerDTO.class);

        ConsumerTypeDTO type = new ConsumerTypeDTO();
        type.setId("type_id");
        type.setLabel("type_label");
        type.setManifest(true);

        CertificateDTO cert = new CertificateDTO();
        cert.setId("123");
        cert.setKey("cert_key");
        cert.setCert("cert_cert");
        cert.setSerial(new CertificateSerialDTO());

        Map<String, String> facts = new HashMap<>();
        for (int i = 0; i < 5; ++i) {
            facts.put("fact-" + i, "value-" + i);
        }

        Set<ConsumerInstalledProductDTO> installedProducts = new HashSet<>();
        for (int i = 0; i < 5; ++i) {
            ConsumerInstalledProductDTO installedProductDTO = cipDTOTest.getPopulatedDTOInstance();
            installedProducts.add(installedProductDTO.setId("cip-" + i));
        }

        Set<CapabilityDTO> capabilityDTOS = new HashSet<>();
        for (int i = 0; i < 5; ++i) {
            CapabilityDTO capabilityDTO = capabilityDTOTest.getPopulatedDTOInstance();
            capabilityDTOS.add(capabilityDTO.setId("capability-" + i));
        }

        Set<String> contentTags = new HashSet<>();
        for (int i = 0; i < 5; ++i) {
            contentTags.add("content-tag-" + i);
        }

        List<GuestIdDTO> guestIdDTOS = new ArrayList<>();
        for (int i = 0; i < 5; ++i) {
            GuestIdDTO guestIdDTO = getGuestIdDTOForTest();
            guestIdDTOS.add(guestIdDTO.id("guest-Id-" + i));
        }

        Set<String> addOns = new HashSet<>();
        for (int i = 0; i < 5; ++i) {
            addOns.add("Add-On-" + i);
        }

        Set<ConsumerActivationKeyDTO> keys = new HashSet<>();
        for (int i = 0; i < 5; ++i) {
            keys.add(new ConsumerActivationKeyDTO("keyId" + i, "keyName" + i));
        }

        NestedOwnerDTO owner = new NestedOwnerDTO().id("OwnerId").displayName("Name").key("12345");

        this.values = new HashMap<>();
        this.values.put("Id", "test-id");
        this.values.put("Uuid", "test-uuid");
        this.values.put("Name", "test-name");
        this.values.put("Username", "test-user-name");
        this.values.put("EntitlementStatus", "test-entitlement-status");
        this.values.put("ServiceLevel", "test-service-level");
        this.values.put("Usage", "test-usage");
        this.values.put("Role", "test-role");
        this.values.put("SystemPurposeStatus", "matched");
        this.values.put("AddOns", addOns);
        this.values.put("ReleaseVersion", "test-release-ver");
        this.values.put("Owner", owner);
        this.values.put("Environment", this.environmentDTOTest.getPopulatedDTOInstance());
        this.values.put("EntitlementCount", 1L);
        this.values.put("Facts", facts);
        this.values.put("LastCheckin", new Date());
        this.values.put("InstalledProducts", installedProducts);
        this.values.put("CanActivate", Boolean.TRUE);
        this.values.put("Capabilities", capabilityDTOS);
        this.values.put("HypervisorId", hypervisorIdDTOTest.getPopulatedDTOInstance());
        this.values.put("ContentTags", contentTags);
        this.values.put("Autoheal", Boolean.TRUE);
        this.values.put("Annotations", "test-annotations");
        this.values.put("ContentAccessMode", "test-content-access-mode");
        this.values.put("Type", type);
        this.values.put("IdCertificate", cert);
        this.values.put("GuestIds", guestIdDTOS);
        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());
        this.values.put("ActivationKeys", keys);

        GuestIdDTO guestIdDTO = getGuestIdDTOForTest();
        guestIdDTO.setGuestId("guest-Id-x");
        this.values.put("addGuestId", guestIdDTO);
        this.values.put("removeGuestId", guestIdDTO.getGuestId());

        ConsumerInstalledProductDTO installedProductDTO = cipDTOTest.getPopulatedDTOInstance();
        installedProductDTO.setProductId("blah");
        this.values.put("addInstalledProduct", installedProductDTO);
        this.values.put("removeInstalledProduct", installedProductDTO.getProductId());

        CertificateDTO idCert = new CertificateDTO();
        cert.setId("cert-id");
        this.values.put("IdCert", idCert);
    }

    private GuestIdDTO getGuestIdDTOForTest() {
        Map<String, String> attributes = new HashMap<>();

        for (int i = 0; i < 5; ++i) {
            attributes.put("attrib-" + i, "value-" + i);
        }

        GuestIdDTO guestIdDTO = new GuestIdDTO()
            .id("test_value")
            .guestId("test_value")
            .attributes(attributes)
            .created(OffsetDateTime.now())
            .updated(OffsetDateTime.now());

        return guestIdDTO;
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

    @Test(expected = IllegalArgumentException.class)
    public void testAddNullToInstalledProducts() throws Exception {
        ConsumerDTO dto = new ConsumerDTO();
        dto.addInstalledProduct(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddNullToGuestIds() throws Exception {
        ConsumerDTO dto = new ConsumerDTO();
        dto.addGuestId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveNullFromInstalledProducts() throws Exception {
        ConsumerDTO dto = new ConsumerDTO();
        dto.removeInstalledProduct(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveNullFromGuestId() throws Exception {
        ConsumerDTO dto = new ConsumerDTO();
        dto.removeGuestId(null);
    }

    @Test
    public void testAddToEmptyInstalledProductCollection() throws Exception {
        ConsumerInstalledProductDTO installedProductDTO = cipDTOTest.getPopulatedDTOInstance();
        installedProductDTO.setProductId("blah");
        ConsumerDTO dto = new ConsumerDTO();
        assertTrue(dto.addInstalledProduct(installedProductDTO));

        assertEquals(1, dto.getInstalledProducts().size());
        assertEquals(installedProductDTO, dto.getInstalledProducts().iterator().next());
    }

    @Test
    public void testAddToEmptyGuestIdCollection() throws Exception {
        GuestIdDTO guestIdDTO = this.getGuestIdDTOForTest();
        guestIdDTO.setGuestId("guest-Id-x");
        ConsumerDTO dto = new ConsumerDTO();
        assertTrue(dto.addGuestId(guestIdDTO));

        assertEquals(1, dto.getGuestIds().size());
        assertEquals(guestIdDTO, dto.getGuestIds().iterator().next());
    }

    @Test
    public void testAddDuplicateToInstalledProductCollection() throws Exception {
        ConsumerInstalledProductDTO installedProductDTO = cipDTOTest.getPopulatedDTOInstance();
        installedProductDTO.setProductId("blah");
        ConsumerDTO dto = new ConsumerDTO();
        assertTrue(dto.addInstalledProduct(installedProductDTO));

        assertEquals(1, dto.getInstalledProducts().size());
        assertEquals(installedProductDTO, dto.getInstalledProducts().iterator().next());

        assertFalse(dto.addInstalledProduct(installedProductDTO));
        assertEquals(1, dto.getInstalledProducts().size());
        assertEquals(installedProductDTO, dto.getInstalledProducts().iterator().next());
    }

    @Test
    public void testAddDuplicateToGuestIdCollection() throws Exception {
        GuestIdDTO guestIdDTO = this.getGuestIdDTOForTest();
        guestIdDTO.setGuestId("guest-Id-x");
        ConsumerDTO dto = new ConsumerDTO();
        assertTrue(dto.addGuestId(guestIdDTO));

        assertEquals(1, dto.getGuestIds().size());
        assertEquals(guestIdDTO, dto.getGuestIds().iterator().next());

        assertFalse(dto.addGuestId(guestIdDTO));
        assertEquals(1, dto.getGuestIds().size());
        assertEquals(guestIdDTO, dto.getGuestIds().iterator().next());
    }

    @Test
    public void testRemoveFromInstalledProductCollectionWhenPresent() throws Exception {
        ConsumerInstalledProductDTO installedProductDTO = cipDTOTest.getPopulatedDTOInstance();
        installedProductDTO.setProductId("blah");
        ConsumerDTO dto = new ConsumerDTO();
        assertTrue(dto.addInstalledProduct(installedProductDTO));

        assertEquals(1, dto.getInstalledProducts().size());
        assertEquals(installedProductDTO, dto.getInstalledProducts().iterator().next());

        assertTrue(dto.removeInstalledProduct(installedProductDTO.getProductId()));
        assertEquals(0, dto.getInstalledProducts().size());
    }

    @Test
    public void testRemoveFromGuestIdCollectionWhenPresent() throws Exception {
        GuestIdDTO guestIdDTO = this.getGuestIdDTOForTest();
        guestIdDTO.setGuestId("guest-Id-x");
        ConsumerDTO dto = new ConsumerDTO();
        assertTrue(dto.addGuestId(guestIdDTO));

        assertEquals(1, dto.getGuestIds().size());
        assertEquals(guestIdDTO, dto.getGuestIds().iterator().next());

        assertTrue(dto.removeGuestId(guestIdDTO.getGuestId()));
        assertEquals(0, dto.getGuestIds().size());
    }

    @Test
    public void testRemoveFromInstalledProductCollectionWhenAbsent() throws Exception {
        ConsumerInstalledProductDTO installedProductDTO = cipDTOTest.getPopulatedDTOInstance();
        installedProductDTO.setProductId("blah");
        ConsumerDTO dto = new ConsumerDTO();
        assertTrue(dto.addInstalledProduct(installedProductDTO));

        assertFalse(dto.removeInstalledProduct("DNE"));

        assertEquals(1, dto.getInstalledProducts().size());
        assertEquals(installedProductDTO, dto.getInstalledProducts().iterator().next());
    }

    @Test
    public void testRemoveFromGuestIdCollectionWhenAbsent() throws Exception {
        GuestIdDTO guestIdDTO = this.getGuestIdDTOForTest();
        guestIdDTO.setGuestId("guest-Id-x");
        ConsumerDTO dto = new ConsumerDTO();
        assertTrue(dto.addGuestId(guestIdDTO));

        assertFalse(dto.removeGuestId("DNE"));

        assertEquals(1, dto.getGuestIds().size());
        assertEquals(guestIdDTO, dto.getGuestIds().iterator().next());
    }

}
