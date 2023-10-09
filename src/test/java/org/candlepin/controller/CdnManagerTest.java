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
package org.candlepin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCertificate;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;



public class CdnManagerTest extends DatabaseTestFixture {
    private CdnManager manager;

    @BeforeEach
    @Override
    public void init() throws Exception {
        super.init();
        manager = injector.getInstance(CdnManager.class);
    }

    @Test
    public void testCreateCdn() throws Exception {
        Cdn cdn = createCdn("test_cdn");
        Cdn fetched = cdnCurator.getByLabel(cdn.getLabel());
        assertNotNull(fetched);
        assertEquals("test_cdn", fetched.getLabel());
    }

    @Test
    public void updateCdn() throws Exception {
        Cdn cdn = createCdn("test_cdn");
        assertEquals("Test CDN", cdn.getName());
        cdn.setName("Updated CDN");
        manager.updateCdn(cdn);

        Cdn fetched = cdnCurator.getByLabel(cdn.getLabel());
        assertNotNull(fetched);
        assertEquals(cdn.getLabel(), fetched.getLabel());
        assertEquals("Updated CDN", fetched.getName());
    }

    @Test
    public void deleteCdn() throws Exception {
        Cdn cdn = createCdn("test_cdn");
        manager.deleteCdn(cdn);
        assertNull(cdnCurator.getByLabel(cdn.getLabel()));
    }

    @Test
    public void deleteCdnUpdatesPoolAssociation() {
        Cdn cdn = createCdn("MyTestCdn");

        Owner owner = this.createOwner();

        Product product = createProduct(owner);
        Pool pool = createPool(owner, product);
        pool.setCdn(cdn);
        poolCurator.create(pool);
        assertNotNull(pool.getCdn());

        manager.deleteCdn(cdn);
        assertNull(cdnCurator.getByLabel(cdn.getLabel()));
        poolCurator.clear();

        Pool updatedPool = poolCurator.get(pool.getId());
        assertNull(updatedPool.getCdn());
    }

    @Test
    public void cdnCertSerialIsRevokedOnCdnDeletion() throws Exception {
        Cdn cdn = createCdn("test_cdn");
        CertificateSerial serial = cdn.getCertificate().getSerial();
        assertNotNull(serial);
        assertFalse(serial.isRevoked());
        manager.deleteCdn(cdn);
        CertificateSerial fetched = certSerialCurator.get(serial.getId());
        assertNotNull(fetched);
        assertTrue(fetched.isRevoked());
    }

    private Cdn createCdn(String label) {
        CertificateSerial serial = certSerialCurator.create(new CertificateSerial(new Date()));
        CdnCertificate cert = new CdnCertificate();
        cert.setKey("key");
        cert.setCert("cert");
        cert.setSerial(serial);

        Cdn cdn = new Cdn(label, "Test CDN", "test.url", cert);
        manager.createCdn(cdn);
        return cdn;
    }

}
