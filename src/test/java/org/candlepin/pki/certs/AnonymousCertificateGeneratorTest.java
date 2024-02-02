/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import static io.smallrye.common.constraint.Assert.assertNotNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.cache.AnonymousCertContent;
import org.candlepin.cache.AnonymousCertContentCache;
import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.model.AnonymousContentAccessCertificate;
import org.candlepin.model.AnonymousContentAccessCertificateCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.model.dto.Content;
import org.candlepin.pki.CertificateCreationException;
import org.candlepin.pki.impl.BouncyCastleKeyPairGenerator;
import org.candlepin.pki.impl.BouncyCastlePemEncoder;
import org.candlepin.pki.impl.BouncyCastleSecurityProvider;
import org.candlepin.pki.impl.Signer;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.test.CertificateReaderForTesting;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;
import org.candlepin.util.X509V3ExtensionUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class AnonymousCertificateGeneratorTest {
    @Mock
    private CertificateSerialCurator serialCurator;
    @Mock
    private AnonymousCloudConsumerCurator anonConsumerCurator;
    @Mock
    private AnonymousContentAccessCertificateCurator anonymousCertificateCurator;
    @Mock
    private EntitlementCurator entitlementCurator;
    @Mock
    private ProductServiceAdapter productAdapter;
    private Configuration config;
    private ObjectMapper objectMapper;
    private X509V3ExtensionUtil extensionUtil;
    private AnonymousCertContentCache contentCache;
    private AnonymousCertificateGenerator generator;

    @BeforeEach
    void setUp() throws CertificateException, IOException {
        this.config = TestConfig.defaults();
        this.objectMapper = ObjectMapperFactory.getObjectMapper();
        this.extensionUtil = spy(new X509V3ExtensionUtil(this.config, this.entitlementCurator, objectMapper));
        this.contentCache = mock(AnonymousCertContentCache.class);
        BouncyCastleSecurityProvider securityProvider = new BouncyCastleSecurityProvider();
        BouncyCastleKeyPairGenerator keyPairGenerator = new BouncyCastleKeyPairGenerator(
            securityProvider, mock(KeyPairDataCurator.class));
        CertificateReaderForTesting certificateReader = new CertificateReaderForTesting();
        this.generator = new AnonymousCertificateGenerator(
            this.extensionUtil,
            this.serialCurator,
            this.anonConsumerCurator,
            this.anonymousCertificateCurator,
            this.productAdapter,
            contentCache,
            new BouncyCastlePemEncoder(),
            keyPairGenerator,
            new Signer(certificateReader),
            () -> new X509CertificateBuilder(certificateReader, securityProvider)
        );
    }

    @Test
    void shouldReturnCachedCertIfPresent() {
        when(this.anonymousCertificateCurator.create(any(AnonymousContentAccessCertificate.class)))
            .thenAnswer(returnsFirstArg());
        when(this.contentCache.get(anyCollection())).thenReturn(createCertContent());
        when(this.serialCurator.create(any(CertificateSerial.class))).thenAnswer(invocation -> {
            CertificateSerial argument = invocation.getArgument(0);
            argument.setSerial(123L);
            return argument;
        });
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        AnonymousContentAccessCertificate result = this.generator.createCertificate(consumer);

        assertNotNull(result);
        verifyNoInteractions(this.productAdapter);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldFailToCreateCertWhenNoProductInfoAvailable(List<ProductInfo> productInfo) {
        when(this.productAdapter.getChildrenByProductIds(anyCollection())).thenReturn(productInfo);
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThatThrownBy(() -> this.generator.createCertificate(consumer))
            .isInstanceOf(CertificateCreationException.class);
    }

    @Test
    void shouldCreateNewCertWhenMissing() {
        List<ProductInfo> productInfo = List.of(
            createProductInfo(), createProductInfo(), createProductInfo()
        );
        when(this.anonymousCertificateCurator.create(any(AnonymousContentAccessCertificate.class)))
            .thenAnswer(returnsFirstArg());
        when(this.productAdapter.getChildrenByProductIds(anyCollection())).thenReturn(productInfo);
        when(this.serialCurator.create(any(CertificateSerial.class))).thenAnswer(invocation -> {
            CertificateSerial argument = invocation.getArgument(0);
            argument.setSerial(123L);
            return argument;
        });
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        AnonymousContentAccessCertificate result = this.generator.createCertificate(consumer);

        assertNotNull(result);
    }

    private AnonymousCertContent createCertContent() {
        return new AnonymousCertContent("content", List.of(
            createContent(), createContent(), createContent()
        ));
    }

    private static Content createContent() {
        Content content = new Content();
        content.setId(TestUtil.randomString("test_id"));
        content.setPath("test_path");
        return content;
    }

    private ProductInfo createProductInfo() {
        ContentInfo content = mock(ContentInfo.class);
        doReturn("content-id").when(content).getId();
        doReturn("content-name").when(content).getName();
        doReturn("type").when(content).getType();
        doReturn("label").when(content).getLabel();
        doReturn("vendor").when(content).getVendor();
        doReturn("gpg-url").when(content).getGpgUrl();
        doReturn(12345L).when(content).getMetadataExpiration();
        doReturn("required-tags").when(content).getRequiredTags();
        doReturn("arches").when(content).getArches();
        doReturn("url").when(content).getContentUrl();
        doReturn("release-version").when(content).getReleaseVersion();

        ProductContentInfo prodContent = mock(ProductContentInfo.class);
        doReturn(content).when(prodContent).getContent();

        ProductInfo prodInfo = mock(ProductInfo.class);
        doReturn(List.of(prodContent)).when(prodInfo).getProductContent();

        return prodInfo;
    }

}
