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

import org.candlepin.config.Configuration;
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
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.impl.bc.BouncyCastleKeyPairGenerator;
import org.candlepin.service.impl.DefaultUniqueIdGenerator;
import org.candlepin.test.CryptoUtil;
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
    void setUp() {
        I18n i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
        Configuration config = TestConfig.defaults();
        CryptoManager cryptoManager = CryptoUtil.getCryptoManager(config);
        KeyPairGenerator kpGen = new BouncyCastleKeyPairGenerator(cryptoManager,
            mock(KeyPairDataCurator.class));

        this.generator = new UeberCertificateGenerator(
            new DefaultUniqueIdGenerator(),
            this.serialCurator,
            this.ownerCurator,
            this.ueberCertificateCurator,
            this.consumerTypeCurator,
            i18n,
            cryptoManager,
            kpGen,
            new X509ExtensionUtil(config),
            CryptoUtil.getPemEncoder());
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
