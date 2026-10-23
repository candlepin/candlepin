/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.pki.certs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.config.TestConfig;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.Content;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.ContentAccessPayload;
import org.candlepin.model.ContentAccessPayloadCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.ProductContent;
import org.candlepin.model.SCACertificate;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.util.ConsumerKeyPairGenerator;
import org.candlepin.util.X509V3ExtensionUtil;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

class ScaPayloadCacheTest {
    @Test
    void cacheHitAvoidsGatheringAndEncoding() throws Exception {
        ScaContentDataProvider provider = mock(ScaContentDataProvider.class);
        ScaPayloadEncoder encoder = mock(ScaPayloadEncoder.class);
        ContentAccessPayloadCurator curator = mock(ContentAccessPayloadCurator.class);
        Owner owner = new Owner().setId("owner").setLastContentUpdate(new Date(0L));
        Consumer consumer = new Consumer().setUuid("consumer").setOwner(owner);
        Scheme scheme = mock(Scheme.class);
        when(scheme.signatureAlgorithm()).thenReturn("SHA256WithRSA");
        ContentAccessPayload cached = new ContentAccessPayload().setOwner(owner).setTimestamp(new Date(1000L));
        when(curator.getContentAccessPayload(eq("owner"), anyString())).thenReturn(cached);

        ContentAccessPayload actual = new ContentAccessPayloadBuilder(provider, encoder, curator)
            .setOwner(owner)
            .setConsumer(consumer)
            .setCryptoScheme(scheme)
            .build();

        assertThat(actual).isSameAs(cached);
        verifyNoInteractions(provider, encoder);
    }
    @Test
    void exportV2AlwaysEncodesSelectedContentWithoutDatabaseAccess() throws Exception {
        ContentCurator contentCurator = mock(ContentCurator.class);
        ScaContentDataProvider provider = new ScaContentDataProvider(TestConfig.defaults(), contentCurator,
            mock(X509V3ExtensionUtil.class));
        ScaPayloadEncoder encoder = mock(ScaPayloadEncoder.class);
        ContentAccessPayloadCurator curator = mock(ContentAccessPayloadCurator.class);
        Owner owner = new Owner().setId("owner");
        Consumer consumer = new Consumer().setUuid("consumer").setOwner(owner);
        Scheme scheme = mock(Scheme.class);
        when(encoder.encode(any(ScaPayloadData.class), eq(scheme))).thenReturn("first payload", "second payload");
        Content repository = new Content("selected").setContentUrl("/repository").setNamespace("local");
        List<ProductContent> selected = new ArrayList<>(List.of(new ProductContent(repository, false)));
        ContentAccessPayloadBuilder builder = new ContentAccessPayloadBuilder(provider, encoder, curator)
            .forExportV2(selected)
            .setOwner(owner)
            .setConsumer(consumer)
            .setCryptoScheme(scheme);
        selected.clear();

        ContentAccessPayload first = builder.build();
        ContentAccessPayload second = builder.forExportV2(List.of()).build();

        assertThat(first).isNotSameAs(second);
        assertThat(first.getPayload()).isEqualTo("first payload");
        assertThat(second.getPayload()).isEqualTo("second payload");
        ArgumentCaptor<ScaPayloadData> data = ArgumentCaptor.forClass(ScaPayloadData.class);
        verify(encoder, times(2)).encode(data.capture(), eq(scheme));
        assertThat(data.getAllValues().getFirst().content()).extracting(ScaContentData::id).containsExactly("selected");
        assertThat(data.getAllValues().getFirst().content().getFirst().enabled()).isFalse();
        assertThat(data.getAllValues().getLast().content()).isEmpty();
        verifyNoInteractions(curator, contentCurator);
    }

    @Test
    void exportV2RejectsNullContent() {
        ContentAccessPayloadBuilder builder = new ContentAccessPayloadBuilder(mock(ScaContentDataProvider.class),
            mock(ScaPayloadEncoder.class), mock(ContentAccessPayloadCurator.class));

        assertThrows(NullPointerException.class, () -> builder.forExportV2(null));
    }

    @Test
    void certificateReuseAvoidsDataPreparationAndEncoding() throws Exception {
        ScaCertificateDataProvider provider = mock(ScaCertificateDataProvider.class);
        ScaCertificateEncoder encoder = mock(ScaCertificateEncoder.class);
        ContentAccessCertificateCurator certificates = mock(ContentAccessCertificateCurator.class);
        CertificateSerialCurator serials = mock(CertificateSerialCurator.class);
        ConsumerKeyPairGenerator keyGenerator = mock(ConsumerKeyPairGenerator.class);
        PemEncoder pemEncoder = mock(PemEncoder.class);
        Consumer consumer = new Consumer().setUuid("consumer");
        KeyPair keyPair = new KeyPair(mock(PublicKey.class), mock(PrivateKey.class));
        byte[] privateKey = {1, 2, 3};
        when(keyGenerator.getConsumerKeyPair(consumer)).thenReturn(keyPair);
        when(pemEncoder.encodeAsBytes(keyPair.getPrivate())).thenReturn(privateKey);
        SCACertificate certificate = new SCACertificate();
        certificate.setKeyAsBytes(privateKey);
        certificate.setSerial(new CertificateSerial(Date.from(Instant.now().plusSeconds(86400L * 365))));
        consumer.setContentAccessCert(certificate);
        ScaCertificateLifecycle lifecycle = new ScaCertificateLifecycle(TestConfig.defaults(), pemEncoder,
            keyGenerator, provider, encoder, certificates, serials);

        assertThat(lifecycle.getCertificate(mock(Scheme.class), consumer, List.of())).isSameAs(certificate);
        verifyNoInteractions(provider, encoder, certificates, serials);
    }
}
