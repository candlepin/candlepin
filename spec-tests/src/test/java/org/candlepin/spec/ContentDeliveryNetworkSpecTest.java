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
package org.candlepin.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.dto.api.client.v1.CdnDTO;
import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.CertificateSerialDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Cdns;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

@SpecTest
public class ContentDeliveryNetworkSpecTest {

    private ApiClient admin;

    @BeforeEach
    public void beforeAll() {
        admin = ApiClients.admin();
    }

    @Test
    public void shouldAllowContentDeliveryNetworkCreation() {
        CdnDTO cdn = admin.cdns().createCdn(Cdns.random());

        assertThat(cdn.getId())
            .isNotNull();
    }

    @Test
    public void shouldAllowContentDeliveryNetworkUpdate() {
        CdnDTO cdn = admin.cdns().createCdn(Cdns.random());

        cdn.setName("Test CDN 2");
        cdn.setUrl("https://special.cdn.test.com");
        CdnDTO newCdn = admin.cdns().updateCdn(cdn.getLabel(), cdn);

        assertThat(newCdn)
            .isNotNull()
            .returns(cdn.getId(), CdnDTO::getId)
            .returns("Test CDN 2", CdnDTO::getName)
            .returns("https://special.cdn.test.com", CdnDTO::getUrl);
    }

    @Test
    public void shouldAllowCertificateToBePutOnCDNOnCreate() {
        CdnDTO cdn = Cdns.withCert();

        CdnDTO registeredCdn = admin.cdns().createCdn(cdn);

        assertThat(registeredCdn.getCertificate())
            .isNotNull()
            .returns(cdn.getCertificate().getKey(), CertificateDTO::getKey)
            .returns(cdn.getCertificate().getCert(), CertificateDTO::getCert);
    }

    @Test
    public void shouldAllowCertificateToBePutOnCDNOnUpdate() {
        CdnDTO cdn = admin.cdns().createCdn(Cdns.random());
        CertificateDTO cert = new CertificateDTO()
            .cert(StringUtil.random("test_cert"))
            .key(StringUtil.random("test_key"));

        CdnDTO updatedCdn = admin.cdns().updateCdn(cdn.getLabel(), cdn.certificate(cert));

        assertThat(updatedCdn.getCertificate())
            .isNotNull()
            .returns(cdn.getCertificate().getKey(), CertificateDTO::getKey)
            .returns(cdn.getCertificate().getCert(), CertificateDTO::getCert);
    }

    @Test
    public void shouldAllowCandlepinToCreateCertificateSerial() {
        CertificateDTO cert = new CertificateDTO()
            .cert(StringUtil.random("test_cert"))
            .key(StringUtil.random("test_key"))
            .serial(new CertificateSerialDTO().expiration(OffsetDateTime.now().plusYears(1L)));

        CdnDTO cdn = admin.cdns().createCdn(Cdns.random().certificate(cert));

        assertThat(cdn.getCertificate())
            .isNotNull()
            .returns(cdn.getCertificate().getKey(), CertificateDTO::getKey)
            .returns(cdn.getCertificate().getCert(), CertificateDTO::getCert)
            .extracting(CertificateDTO::getSerial)
            .isNotNull();
    }

    @Test
    public void shouldAllowCandlepinToUpdateCertificateSerial() {
        CertificateDTO cert = new CertificateDTO()
            .cert(StringUtil.random("test_cert"))
            .key(StringUtil.random("test_key"))
            .serial(new CertificateSerialDTO().expiration(OffsetDateTime.now().plusYears(1L)));
        CdnDTO cdn = admin.cdns().createCdn(Cdns.random().certificate(cert));

        assertThat(cdn.getCertificate())
            .isNotNull()
            .returns(cdn.getCertificate().getKey(), CertificateDTO::getKey)
            .returns(cdn.getCertificate().getCert(), CertificateDTO::getCert)
            .extracting(CertificateDTO::getSerial)
            .isNotNull();

        OffsetDateTime expectedExpiration = OffsetDateTime.now().plusYears(1L).plusMonths(1);
        cert
            .key(StringUtil.random("m_test_key"))
            .cert(StringUtil.random("m_test_cert"))
            .serial(new CertificateSerialDTO().expiration(expectedExpiration));

        admin.cdns().updateCdn(cdn.getLabel(), cdn.certificate(cert));

        assertThat(cdn.getCertificate())
            .isNotNull()
            .returns(cdn.getCertificate().getKey(), CertificateDTO::getKey)
            .returns(cdn.getCertificate().getCert(), CertificateDTO::getCert)
            .extracting(CertificateDTO::getSerial)
            .returns(expectedExpiration, CertificateSerialDTO::getExpiration);
    }

    @Test
    public void shouldAllowDeletionWithCertificate() {
        CertificateDTO cert = new CertificateDTO()
            .cert(StringUtil.random("test_cert"))
            .key(StringUtil.random("test_key"))
            .serial(new CertificateSerialDTO().expiration(OffsetDateTime.now().plusYears(1L)));

        CdnDTO cdn = admin.cdns().createCdn(Cdns.random().certificate(cert));
        admin.cdns().deleteCdn(cdn.getLabel());
        List<CdnDTO> cdnList = admin.cdns().getContentDeliveryNetworks();

        assertThat(cdnList)
            .map(CdnDTO::getLabel)
            .doesNotContain(cdn.getLabel());
    }
}
