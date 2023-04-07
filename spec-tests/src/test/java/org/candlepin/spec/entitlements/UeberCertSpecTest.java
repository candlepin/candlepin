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
package org.candlepin.spec.entitlements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.UeberCertificateDTO;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.client.cert.X509Cert;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mozilla.jss.netscape.security.util.DerValue;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@SpecTest
public class UeberCertSpecTest {

    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerProductApi ownerProductApi;
    private static OwnerContentApi ownerContentApi;
    final String REDHAT_OID = "1.3.6.1.4.1.2312.9";

    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
        ownerContentApi = client.ownerContent();
    }

    @Test
    public void shouldOwnerCanBeDeleted() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ownerApi.createUeberCertificate(owner.getKey());
        ownerApi.deleteOwner(owner.getKey(), false, false);
        assertNotFound(() -> ownerApi.getOwner(owner.getKey()));
    }

    @Test
    public void shouldContainAllContentForTheEntireOrg() {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());

        ProductDTO prod1 = ownerProductApi.createProductByOwner(
            owner1.getKey(), Products.randomEng().name("test product 1"));
        ProductDTO prod2 = ownerProductApi.createProductByOwner(
            owner1.getKey(), Products.randomEng().name("test product 2"));
        ProductDTO prod3 = ownerProductApi.createProductByOwner(
            owner1.getKey(), Products.randomEng().name("test product 3"));

        ContentDTO content1 = ownerContentApi.createContent(owner1.getKey(), Contents.random());
        ContentDTO content2 = ownerContentApi.createContent(owner1.getKey(), Contents.random());
        ContentDTO content3 = ownerContentApi.createContent(owner1.getKey(), Contents.random());

        prod1 = ownerProductApi.addContent(owner1.getKey(), prod1.getId(), content1.getId(), true);
        prod1 = ownerProductApi.addContent(owner1.getKey(), prod1.getId(), content2.getId(), true);
        prod2 = ownerProductApi.addContent(owner1.getKey(), prod2.getId(), content3.getId(), true);

        ownerApi.createPool(owner1.getKey(), Pools.random()
            .productId(prod1.getId())
            .contractNumber("12345")
            .accountNumber("6789")
            .orderNumber("order1"));
        ownerApi.createPool(owner1.getKey(), Pools.random()
            .productId(prod2.getId())
            .contractNumber("abcde")
            .accountNumber("fghi")
            .orderNumber("order2"));
        ownerApi.createPool(owner1.getKey(), Pools.random()
            .productId(prod3.getId())
            .contractNumber("qwert")
            .accountNumber("yuio")
            .orderNumber("order3"));

        //  generate and verify cert
        UeberCertificateDTO ueberCert = ownerApi.createUeberCertificate(owner1.getKey());
        assertNotNull(ueberCert);

        X509Certificate x509 = X509Cert.parseCertificate(ueberCert.getCert());
        assertThat(x509.getNotAfter()).isEqualTo(Instant.parse("2049-12-01T13:00:00.00Z"));

        String certProudct = null;
        String certContent = null;
        Pattern pattern = Pattern.compile("\\A(1|2)\\.(.+)\\.1(?:\\.(\\d))?");
        for (String oid : x509.getNonCriticalExtensionOIDs().stream()
            .filter(x -> x.startsWith(REDHAT_OID)).collect(Collectors.toList())) {
            String extId = oid.substring(REDHAT_OID.length() + 1);
            Matcher matcher = pattern.matcher(extId);
            if (matcher.find()) {
                String[] matched = extId.split("\\.");
                if (matched.length == 3 && matched[0].equals("1")) {
                    certProudct = getExtensionValue(x509, oid);
                }
                else if (matched.length == 4 && matched[0].equals("2") && matched[3].equals("2")) {
                    certContent = getExtensionValue(x509, oid);
                }
            }
        }
        assertThat(certProudct).isNotNull().endsWith("ueber_product");
        assertThat(certContent).isNotNull().endsWith("ueber_content");
    }

    @Test
    public void shouldHandleConcurrentRequestsToGenerateCertForAnWwner() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        List<Runnable> threads = List.of(
            () -> generateCert(owner),
            () -> generateCert(owner),
            () -> generateCert(owner),
            () -> generateCert(owner),
            () -> generateCert(owner));
        runAllThreads(threads);

        // Verify that the cert can be generated again.
        assertNotNull(ownerApi.createUeberCertificate(owner.getKey()));
    }

    private String getExtensionValue(X509Certificate cert, String extensionId) {
        try {
            DerValue value = new DerValue(cert.getExtensionValue(extensionId));
            byte[] octetString = value.getOctetString();
            return new DerValue(octetString).toString();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateCert(OwnerDTO owner) {
        assertNotNull(ownerApi.createUeberCertificate(owner.getKey()));
    }

    private static void runAllThreads(Collection<Runnable> threads) {
        Set<Thread> toRun = threads.stream()
            .map(Thread::new)
            .collect(Collectors.toSet());
        for (Thread t : toRun) {
            t.start();
        }
        for (Thread t : toRun) {
            try {
                t.join();
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
