/**
 * Copyright (c) 2009 Red Hat, Inc.
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

package org.fedoraproject.candlepin.resource.test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.resource.CertificateResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.wideplay.warp.persist.PersistenceService;
import com.wideplay.warp.persist.UnitOfWork;

import static org.junit.Assert.*;

public class CertificateResourceTest extends DatabaseTestFixture {
    
    private CertificateResource certResource;
    private ConsumerCurator consumerRepository;
    private EntitlementPoolCurator epCurator;
    private OwnerCurator ownerCurator;
    private String sampleCertXml;
    
    @Before
    public void createObjects() throws Exception {

        Injector injector = Guice.createInjector(
                new CandlePingTestingModule(),
                PersistenceService.usingJpa()
                    .across(UnitOfWork.TRANSACTION)
                    .buildModule()
        );

        consumerRepository = injector.getInstance(ConsumerCurator.class);
        epCurator = injector.getInstance(EntitlementPoolCurator.class);
        ownerCurator = injector.getInstance(OwnerCurator.class);
        
        certResource = new CertificateResource();
        certResource.setOwnerCurator(ownerCurator);
        
        
        InputStream is = this.getClass().getResourceAsStream(
                "/org/fedoraproject/candlepin/resource/test/spacewalk-public.cert");
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder builder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            builder.append(line + "\n");
        }
        sampleCertXml = builder.toString();
    }
    
    @Test
    public void simpleUploadCertProductsCreated() {
        certResource.upload(TestUtil.xmlToBase64String(sampleCertXml));
        // TODO: check that products got created!
    }

    @Test
    public void entitlementPoolCreation() {
        Owner o = TestUtil.createOwner();
        ownerCurator.create(o);
        String encoded = TestUtil.xmlToBase64String(sampleCertXml);
        certResource.upload(encoded);
        List<EntitlementPool> entPools = epCurator.listByOwner(o);
        assertEquals(5, entPools.size());
    }

    @Test
    public void channelFamilyCreation() {
        // TODO!!!!!! Current test cert has no channel families.
    }

}
