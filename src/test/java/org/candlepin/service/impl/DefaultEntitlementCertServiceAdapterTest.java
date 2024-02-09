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
package org.candlepin.service.impl;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.pki.certs.EntitlementCertificateGenerator;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.cert.CertificateException;


@ExtendWith(MockitoExtension.class)
public class DefaultEntitlementCertServiceAdapterTest {
    @Mock
    private CertificateSerialCurator serialCurator;
    @Mock
    private EntitlementCertificateCurator entitlementCertificateCurator;
    @Mock
    private EntitlementCertificateGenerator entitlementCertificateGenerator;
    private DefaultEntitlementCertServiceAdapter certServiceAdapter;

    @BeforeEach
    public void setUp() throws CertificateException, IOException {
        certServiceAdapter = new DefaultEntitlementCertServiceAdapter(
            entitlementCertificateCurator, serialCurator, this.entitlementCertificateGenerator);
    }

    @Test
    public void shouldGenerateEntitlementCertificate() throws IOException {
        Owner owner = TestUtil.createOwner();
        Consumer consumer = TestUtil.createConsumer(owner);
        Pool pool = TestUtil.createPool(owner);
        Entitlement entitlement = TestUtil.createEntitlement(owner, consumer, pool, null)
            .setQuantity(10);
        Product product = new Product();

        this.certServiceAdapter.generateEntitlementCert(entitlement, product);

        verify(this.entitlementCertificateGenerator)
            .generate(eq(consumer), anyMap(), anyMap(), anyMap(), eq(true));
    }
}
