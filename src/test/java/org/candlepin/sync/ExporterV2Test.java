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

package org.candlepin.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Principal;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.KeyPairData;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;
import org.candlepin.test.CryptoUtil;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.ObjectMapper;

import java.security.KeyException;
import java.security.KeyPair;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class ExporterV2Test {

    @Mock
    private PrincipalProvider principalProvider;
    @Mock
    private ConsumerTypeCurator consumerTypeCurator;
    @Mock
    private EnvironmentCurator environmentCurator;
    @Mock
    private OwnerCurator ownerCurator;

    private DevConfig config;
    private ObjectMapper mapper;
    private CryptoManager cryptoManager;
    private ModelTranslator modelTranslator;

    private final ConsumerType systemType = new ConsumerType("system").setId("ct-1");

    @BeforeEach
    void setUp() {
        Principal principal = Mockito.mock(Principal.class);

        this.config = TestConfig.defaults();
        this.mapper = ObjectMapperFactory.getSyncObjectMapper(this.config);
        this.cryptoManager = CryptoUtil.getCryptoManager(this.config);
        this.modelTranslator = new StandardTranslator(consumerTypeCurator, environmentCurator, ownerCurator);

        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");

        when(principalProvider.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testExportUser");
        when(consumerTypeCurator.getConsumerType(any())).thenReturn(systemType);
    }

    @Test
    public void testExportV2() throws KeyException, ExportCreationException {
        Entitlement ent = mock(Entitlement.class);

        Owner owner = TestUtil.createOwner("Example-Corporation");

        Product prod12345 = TestUtil.createProduct("12345", "RHEL Product");
        prod12345.setMultiplier(1L);
        prod12345.setCreated(new Date());
        prod12345.setUpdated(new Date());
        prod12345.setAttributes(Collections.emptyMap());

        Product mktProd = TestUtil.createProduct("MKT-prod", "RHEL Product");
        mktProd.setMultiplier(1L);
        mktProd.setCreated(new Date());
        mktProd.setUpdated(new Date());
        mktProd.setAttributes(Collections.emptyMap());

        Product mktSubProd = TestUtil.createProduct("MKT-sub-prod", "Sub Product");
        mktSubProd.setMultiplier(1L);
        mktSubProd.setCreated(new Date());
        mktSubProd.setUpdated(new Date());
        mktSubProd.setAttributes(Collections.emptyMap());

        Product subProvidedProduct332211 = TestUtil.createProduct("332211", "Sub Product");
        subProvidedProduct332211.setMultiplier(1L);
        subProvidedProduct332211.setCreated(new Date());
        subProvidedProduct332211.setUpdated(new Date());
        subProvidedProduct332211.setAttributes(Collections.emptyMap());

        mktProd.addProvidedProduct(prod12345);
        mktProd.setDerivedProduct(mktSubProd);
        mktSubProd.addProvidedProduct(subProvidedProduct332211);

        Pool pool = TestUtil.createPool(owner)
            .setId("MockedPoolId")
            .setProduct(mktProd);

        when(ent.getPool()).thenReturn(pool);

        IdentityCertificate idcert = new IdentityCertificate();
        KeyPairData keyPairData = this.generateConsumerKeyPairData(null);

        Consumer consumer = new Consumer()
            .setIdCert(idcert)
            .setKeyPairData(keyPairData)
            .setType(systemType)
            .setEntitlements(Set.of(ent))
            .setOwner(owner)
            .setName("Test Consumer")
            .setUuid("test-consumer-id");

        ExporterV2 exporter = this.createExporter();
        exporter.getFullExport(consumer);
    }

    private ExporterV2 createExporter() {
        return new ExporterV2(
            mapper,
            config,
            principalProvider,
            modelTranslator
        );
    }

    private KeyPairData generateConsumerKeyPairData(Scheme scheme) throws KeyException {
        if (scheme == null) {
            scheme = this.cryptoManager.getDefaultCryptoScheme();
        }

        KeyPair keypair = CryptoUtil.generateKeyPair(scheme);

        return new KeyPairData()
            .setPublicKeyData(keypair.getPublic().getEncoded())
            .setPrivateKeyData(keypair.getPrivate().getEncoded())
            .setAlgorithm(scheme.keyAlgorithm());
    }
}
