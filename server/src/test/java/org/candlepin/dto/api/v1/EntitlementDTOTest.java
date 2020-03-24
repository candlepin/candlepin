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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.candlepin.dto.AbstractDTOTest;

import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * Test suite for the EntitlementDTO class
 */
public class EntitlementDTOTest  extends AbstractDTOTest<EntitlementDTO> {

    protected Map<String, Object> values;

    public EntitlementDTOTest() {
        super(EntitlementDTO.class);

        this.values = new HashMap<>();

        NestedOwnerDTO owner = new NestedOwnerDTO();
        owner.setId("owner_id");
        owner.setKey("owner_key");
        owner.setDisplayName("owner_name");
        PoolDTO pool = new PoolDTO();
        pool.setId("pool_id");
        pool.setProductId("pool_product_id");
        pool.setProductName("pool_product_name");

        ConsumerDTO consumer = new ConsumerDTO();
        consumer.setId("consumer_id");
        consumer.setUuid("consumer_uuid");
        consumer.setName("consumer_name");
        consumer.setUsername("consumer_username");
        consumer.setType(new ConsumerTypeDTO());

        Set<CertificateDTO> certs = new HashSet<>();
        CertificateDTO certificate = new CertificateDTO();
        certificate.setId("cert-id");
        certificate.setKey("cert-key");
        certificate.setCert("cert");
        certificate.setSerial(new CertificateSerialDTO());
        certs.add(certificate);

        this.values.put("Id", "test-id");
        this.values.put("Owner", owner);
        this.values.put("Pool", pool);
        this.values.put("Consumer", consumer);
        this.values.put("Quantity", 1);
        this.values.put("DeletedFromPool", false);
        this.values.put("Certificates", certs);
        this.values.put("StartDate", new Date());
        this.values.put("EndDate", new Date());
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

    @Test
    public void testAddCertificateWithAbsentCertificate() {
        EntitlementDTO dto = new EntitlementDTO();

        CertificateDTO certDTO = new CertificateDTO();
        certDTO.setId("cert-id-1");
        certDTO.setKey("cert-key-1");
        certDTO.setCert("cert-cert-1");
        certDTO.setSerial(new CertificateSerialDTO());
        assertTrue(dto.addCertificate(certDTO));
    }

    @Test
    public void testAddCertificateWithPresentCertificate() {
        EntitlementDTO dto = new EntitlementDTO();

        CertificateDTO certDTO = new CertificateDTO();
        certDTO.setId("cert-id-2");
        certDTO.setKey("cert-key-2");
        certDTO.setCert("cert-cert-2");
        certDTO.setSerial(new CertificateSerialDTO());
        assertTrue(dto.addCertificate(certDTO));

        CertificateDTO certDTO2 = new CertificateDTO();
        certDTO2.setId("cert-id-2");
        certDTO2.setKey("cert-key-2");
        certDTO2.setCert("cert-cert-2");
        certDTO2.setSerial(new CertificateSerialDTO());
        assertFalse(dto.addCertificate(certDTO2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddCertificateWithNullInput() {
        EntitlementDTO dto = new EntitlementDTO();
        dto.addCertificate(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddCertificateWithEmptyId() {
        EntitlementDTO dto = new EntitlementDTO();

        CertificateDTO certDTO = new CertificateDTO();
        certDTO.setId("");
        certDTO.setKey("cert-key-3");
        certDTO.setCert("cert-cert-3");
        certDTO.setSerial(new CertificateSerialDTO());
        dto.addCertificate(certDTO);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddCertificateWithEmptyKey() {
        EntitlementDTO dto = new EntitlementDTO();

        CertificateDTO certDTO = new CertificateDTO();
        certDTO.setId("cert-id-4");
        certDTO.setKey("");
        certDTO.setCert("cert-cert-4");
        certDTO.setSerial(new CertificateSerialDTO());
        dto.addCertificate(certDTO);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddCertificateWithEmptyCert() {
        EntitlementDTO dto = new EntitlementDTO();

        CertificateDTO certDTO = new CertificateDTO();
        certDTO.setId("cert-id-5");
        certDTO.setKey("cert-key-5");
        certDTO.setCert("");
        certDTO.setSerial(new CertificateSerialDTO());
        dto.addCertificate(certDTO);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddCertificateWithNullSerial() {
        EntitlementDTO dto = new EntitlementDTO();

        CertificateDTO certDTO = new CertificateDTO();
        certDTO.setId("cert-id-6");
        certDTO.setKey("cert-key-6");
        certDTO.setCert("cert-cert-6");
        certDTO.setSerial(null);
        dto.addCertificate(certDTO);
    }
}
