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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.config.TestConfig;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.UeberCertificate;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.impl.BouncyCastleKeyPairGenerator;
import org.candlepin.pki.impl.BouncyCastlePemEncoder;
import org.candlepin.pki.impl.BouncyCastleSecurityProvider;
import org.candlepin.pki.impl.BouncyCastleSubjectKeyIdentifierWriter;
import org.candlepin.service.impl.DefaultUniqueIdGenerator;
import org.candlepin.test.CertificateReaderForTesting;
import org.candlepin.test.TestUtil;
import org.candlepin.util.X509ExtensionUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Locale;

@ExtendWith(MockitoExtension.class)
class UeberCertificateGeneratorTest {
    @Mock
    private CertificateSerialCurator serialCurator;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private UeberCertificateCurator ueberCertificateCurator;
    @Mock
    private ConsumerTypeCurator consumerTypeCurator;
    private UeberCertificateGenerator generator;

    @BeforeEach
    void setUp() throws CertificateException, IOException {
        I18n i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
        BouncyCastleSecurityProvider securityProvider = new BouncyCastleSecurityProvider();
        CertificateReaderForTesting certificateAuthority = new CertificateReaderForTesting();
        SubjectKeyIdentifierWriter subjectKeyIdentifierWriter = new BouncyCastleSubjectKeyIdentifierWriter();
        X509CertificateBuilder certificateBuilder = new X509CertificateBuilder(
            certificateAuthority, securityProvider, subjectKeyIdentifierWriter);

        this.generator = new UeberCertificateGenerator(
            new DefaultUniqueIdGenerator(),
            new X509ExtensionUtil(TestConfig.defaults()),
            this.serialCurator,
            this.ownerCurator,
            this.ueberCertificateCurator,
            this.consumerTypeCurator,
            new BouncyCastleKeyPairGenerator(securityProvider, mock(KeyPairDataCurator.class)),
            new BouncyCastlePemEncoder(),
            i18n,
            () -> certificateBuilder
        );
    }

    @Test
    void shouldRegenerateExistingCertificate() {
        Owner owner = TestUtil.createOwner();
        when(this.ownerCurator.getByKey(anyString())).thenReturn(owner);
        when(this.consumerTypeCurator.getByLabel(anyString(), anyBoolean())).thenReturn(createType());
        when(this.serialCurator.create(any(CertificateSerial.class))).thenAnswer(invocation -> {
            CertificateSerial argument = invocation.getArgument(0);
            argument.setSerial((long) TestUtil.randomInt());
            return argument;
        });

        UeberCertificate certificate = this.generator.generate("owner_key", "username");

        verify(this.ueberCertificateCurator).deleteForOwner(owner);
        Assertions.assertThat(certificate)
            .isNotNull();
    }

    @Test
    void shouldFailForMissingOwner() {
        Assertions.assertThatThrownBy(() -> this.generator.generate("owner_key", "username"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldHandleFailures() {
        Owner owner = TestUtil.createOwner();
        when(this.ownerCurator.getByKey(anyString())).thenReturn(owner);
        when(this.ueberCertificateCurator.deleteForOwner(owner)).thenThrow(RuntimeException.class);

        Assertions.assertThatThrownBy(() -> this.generator.generate("owner_key", "username"))
            .isInstanceOf(BadRequestException.class);
    }

    private static ConsumerType createType() {
        return new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN)
            .setId(TestUtil.randomString());
    }
}
