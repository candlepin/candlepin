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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;



/**
 * Test suite for the EntitlementCertificateCurator class
 */
public class EntitlementCertificateCuratorTest extends DatabaseTestFixture {

    private Owner owner;
    private Consumer consumer;
    private Product product;
    private Pool pool;

    @BeforeEach
    public void setup() {
        this.owner = this.createOwner();
        this.consumer = this.createConsumer(this.owner);
        this.product = this.createProduct(this.owner);
        this.pool = this.createPool(this.owner, this.product);
    }

    @Test
    public void testDeleteSingleCertBySingleEntitlementId() {
        Entitlement ent1 = this.createEntitlement(this.owner, this.consumer, this.pool);
        Entitlement ent2 = this.createEntitlement(this.owner, this.consumer, this.pool);

        EntitlementCertificate cert1 = this.createEntitlementCertificate(ent1, "key1", "cert1");
        EntitlementCertificate cert2a = this.createEntitlementCertificate(ent2, "key2a", "cert2a");
        EntitlementCertificate cert2b = this.createEntitlementCertificate(ent2, "key2b", "cert2b");

        // Verify initial state
        assertNotNull(ent1.getId());
        assertNotNull(ent2.getId());
        assertNotEquals(ent1.getId(), ent2.getId());

        assertNotNull(cert1.getId());
        assertNotNull(cert2a.getId());
        assertNotNull(cert2b.getId());

        this.entitlementCertificateCurator.clear();

        // Verify that we can fetch all three certs generally
        Collection<EntitlementCertificate> certs = this.entitlementCertificateCurator.listAll().list();
        assertNotNull(certs);
        assertEquals(3, certs.size());

        this.entitlementCertificateCurator.clear();

        // Delete certs for ent1, verify we have one cert remaining
        int count = this.entitlementCertificateCurator.deleteByEntitlementIds(ent1.getId());
        assertEquals(1, count);

        // Verify state after deletion
        certs = this.entitlementCertificateCurator.listAll().list();
        assertNotNull(certs);
        assertEquals(2, certs.size());

        ent1 = this.entitlementCurator.get(ent1.getId());
        assertNotNull(ent1);
        assertNotNull(ent1.getCertificates());
        assertEquals(0, ent1.getCertificates().size());

        ent2 = this.entitlementCurator.get(ent2.getId());
        assertNotNull(ent2);
        assertNotNull(ent2.getCertificates());
        assertEquals(2, ent2.getCertificates().size());

        Set<String> remaining = ent2.getCertificates().stream()
            .map(c -> c.getId())
            .collect(Collectors.toSet());

        assertTrue(remaining.contains(cert2a.getId()));
        assertTrue(remaining.contains(cert2b.getId()));
    }

    @Test
    public void testDeleteMultipleCertsBySingleEntitlementId() {
        Entitlement ent1 = this.createEntitlement(this.owner, this.consumer, this.pool);
        Entitlement ent2 = this.createEntitlement(this.owner, this.consumer, this.pool);

        EntitlementCertificate cert1 = this.createEntitlementCertificate(ent1, "key1", "cert1");
        EntitlementCertificate cert2a = this.createEntitlementCertificate(ent2, "key2a", "cert2a");
        EntitlementCertificate cert2b = this.createEntitlementCertificate(ent2, "key2b", "cert2b");

        // Verify initial state
        assertNotNull(ent1.getId());
        assertNotNull(ent2.getId());
        assertNotEquals(ent1.getId(), ent2.getId());

        assertNotNull(cert1.getId());
        assertNotNull(cert2a.getId());
        assertNotNull(cert2b.getId());

        this.entitlementCertificateCurator.clear();

        // Verify that we can fetch all three certs generally
        Collection<EntitlementCertificate> certs = this.entitlementCertificateCurator.listAll().list();
        assertNotNull(certs);
        assertEquals(3, certs.size());

        this.entitlementCertificateCurator.clear();

        // Delete certs for ent1, verify we have one cert remaining
        int count = this.entitlementCertificateCurator.deleteByEntitlementIds(ent2.getId());
        assertEquals(2, count);

        // Verify state after deletion
        certs = this.entitlementCertificateCurator.listAll().list();
        assertNotNull(certs);
        assertEquals(1, certs.size());

        ent1 = this.entitlementCurator.get(ent1.getId());
        assertNotNull(ent1);
        assertNotNull(ent1.getCertificates());
        assertEquals(1, ent1.getCertificates().size());

        Set<String> remaining = ent1.getCertificates().stream()
            .map(c -> c.getId())
            .collect(Collectors.toSet());

        assertTrue(remaining.contains(cert1.getId()));

        ent2 = this.entitlementCurator.get(ent2.getId());
        assertNotNull(ent2);
        assertNotNull(ent2.getCertificates());
        assertEquals(0, ent2.getCertificates().size());

    }

    @Test
    public void testDeleteDoesntAffectUnsavedCerts() {
        Entitlement ent1 = this.createEntitlement(this.owner, this.consumer, this.pool);
        EntitlementCertificate cert1 = this.createEntitlementCertificate(ent1, "key1", "cert1");

        // Verify initial state
        assertNotNull(ent1.getId());
        assertNotNull(cert1.getId());

        // Create a new, unsaved cert
        EntitlementCertificate certN = new EntitlementCertificate();
        CertificateSerial certSerial = new CertificateSerial(new Date());
        this.certSerialCurator.create(certSerial);

        certN.setKeyAsBytes("keyN".getBytes());
        certN.setCertAsBytes("certN".getBytes());
        certN.setSerial(certSerial);

        ent1.addCertificate(certN);

        // Delete existing certs for ent1, without affecting the new, unsaved cert.
        int count = this.entitlementCertificateCurator.deleteByEntitlementIds(ent1.getId());
        assertEquals(1, count);

        // Merge the changes on entitlement; we should end up with the entitlement only having the
        // new cert
        ent1 = this.entitlementCurator.merge(ent1);
        this.entitlementCurator.clear();

        ent1 = this.entitlementCurator.get(ent1.getId());
        assertNotNull(ent1);
        assertNotNull(ent1.getCertificates());
        assertEquals(1, ent1.getCertificates().size());

        // Verify the serial on the remaining cert is what we expect it to be
        EntitlementCertificate remaining = ent1.getCertificates().iterator().next();
        CertificateSerial remSerial = remaining.getSerial();

        assertEquals(certSerial.getId(), remSerial.getId());
    }

}
