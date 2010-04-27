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
package org.fedoraproject.candlepin.service.impl.stub;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.EntitlementCertificateCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.service.BaseEntitlementCertServiceAdapter;

import com.google.inject.Inject;

/**
 * StubEntitlementCertServiceAdapter
 * 
 * Generating an entitlement cert is expensive, this class stubs the process out.
 */
public class StubEntitlementCertServiceAdapter extends BaseEntitlementCertServiceAdapter {

    private static Logger log = Logger
        .getLogger(StubEntitlementCertServiceAdapter.class);
    
    @Inject
    public StubEntitlementCertServiceAdapter(
        EntitlementCertificateCurator entCertCurator) {

        this.entCertCurator = entCertCurator;
    }

    @Override
    public EntitlementCertificate generateEntitlementCert(Consumer consumer,
        Entitlement entitlement, Subscription sub, Product product, Date endDate, 
        Long serialNumber) throws GeneralSecurityException, IOException {
        log.debug("Generating entitlement cert for:");
        log.debug("   consumer: " + consumer.getUuid());
        log.debug("   product: " + product.getId());
        log.debug("   end date: " + endDate);
        
        EntitlementCertificate cert = new EntitlementCertificate();
        cert.setSerial(serialNumber);
        cert.setKey("---- STUB KEY -----".getBytes());
        cert.setCert("---- STUB CERT -----".getBytes());
        cert.setEntitlement(entitlement);
        entitlement.getCertificates().add(cert);
        
        log.debug("Generated cert: " + serialNumber);
        log.debug("Key: " + cert.getKeyAsString());
        log.debug("Cert: " + cert.getCertAsString());
        entCertCurator.create(cert);
        
        return cert;
    }

}

