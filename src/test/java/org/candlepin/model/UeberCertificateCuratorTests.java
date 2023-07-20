/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.candlepin.test.DatabaseTestFixture;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.PersistenceException;



/**
 * UeberCertificateCuratorTests
 */
public class UeberCertificateCuratorTests extends DatabaseTestFixture {

    private Owner owner;

    @BeforeEach
    public void setupTest() {
        owner = this.createOwner("uebertest");
    }

    @Test
    public void ensureCertificateCreationAndGet() {
        UeberCertificate cert = this.createUeberCert(owner);
        assertEquals(cert, ueberCertificateCurator.get(cert.getId()));
        assertEquals(cert, ueberCertificateCurator.findForOwner(owner));
    }

    @Test
    public void ensureCertificateDeletion() {
        this.createUeberCert(owner);
        this.ueberCertificateCurator.deleteForOwner(owner);
        assertNull(this.ueberCertificateCurator.findForOwner(owner));
    }

    @Test
    public void ensureDuplicateCertificateCreationFailsForAnOwner() {
        // This should never happen in code, but adding a test to make
        // sure that if there was ever an attempt to add more than one
        // cert to an owner, the DB would block it.
        //
        // The second create should throw a constraint violation.
        this.createUeberCert(owner);

        try {
            this.createUeberCert(owner);
            fail("Expected an exception due to multiple certs for the owner.");
        }
        catch (PersistenceException e) {
            assertTrue(e.getCause() != null && e.getCause() instanceof ConstraintViolationException);
        }
    }

    @Test
    public void ensureCertificateDeletionRevokesCertSerial() {
        UeberCertificate cert = this.createUeberCert(owner);
        CertificateSerial serial = cert.getSerial();
        this.ueberCertificateCurator.deleteForOwner(owner);
        assertNull(this.ueberCertificateCurator.findForOwner(owner));
        CertificateSerial fetchedSerial = certSerialCurator.get(serial.getId());
        assertTrue(fetchedSerial.isRevoked(), "Serial should have been revoked");
    }

    private UeberCertificate createUeberCert(Owner owner) {
        CertificateSerial serial = new CertificateSerial();
        this.certSerialCurator.create(serial);

        UeberCertificate uc = new UeberCertificate();
        uc.setOwner(owner);
        uc.setSerial(serial);
        uc.setCert("A FAKE PEM STRING");
        uc.setKey("A Fake Key");

        return this.ueberCertificateCurator.create(uc);
    }

}
