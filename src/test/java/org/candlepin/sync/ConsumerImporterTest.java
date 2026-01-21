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
package org.candlepin.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.dto.manifest.v1.ConsumerDTO;
import org.candlepin.dto.manifest.v1.ConsumerTypeDTO;
import org.candlepin.dto.manifest.v1.OwnerDTO;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;
import java.util.Map;


public class ConsumerImporterTest {

    private ConsumerImporter importer;
    private ObjectMapper mapper;
    private OwnerCurator curator;
    private CertificateSerialCurator serialCurator;
    private IdentityCertificateCurator idCertCurator;
    private I18n i18n;

    @BeforeEach
    public void setUp() {
        curator = mock(OwnerCurator.class);
        serialCurator = mock(CertificateSerialCurator.class);
        idCertCurator = mock(IdentityCertificateCurator.class);
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        importer = new ConsumerImporter(curator, idCertCurator, i18n, serialCurator);

        DevConfig config = TestConfig.custom(Map.of(
            ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false"));
        this.mapper = ObjectMapperFactory.getSyncObjectMapper(config);
    }

    @Test
    public void importShouldCreateAValidConsumer() throws IOException {
        ConsumerDTO consumer = importer.createObject(mapper,
            new StringReader("{\"uuid\":\"test-uuid\",\"name\":\"test-name\"}"));

        assertEquals("test-uuid", consumer.getUuid());
        assertEquals("test-name", consumer.getName());
    }

    @Test
    public void importHandlesUnknownPropertiesGracefully() throws Exception {
        // Override default config to error out on unknown properties:
        DevConfig config = TestConfig.custom(Map.of(
            ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false"));
        this.mapper = ObjectMapperFactory.getSyncObjectMapper(config);

        ConsumerDTO consumer = importer.createObject(
            mapper, new StringReader("{\"uuid\":\"test-uuid\", \"unknown\":\"notreal\"}"));
        assertEquals("test-uuid", consumer.getUuid());
    }

    @Test
    public void importFailsOnUnknownPropertiesWithNonDefaultConfig() {
        // Override default config to error out on unknown properties:
        DevConfig config = TestConfig.custom(Map.of(
            ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "true"));
        this.mapper = ObjectMapperFactory.getSyncObjectMapper(config);

        String json = "{\"uuid\":\"test-uuid\", \"unknown\":\"notreal\"}";
        assertThrows(DatabindException.class,
            () -> importer.createObject(mapper, new StringReader(json)));
    }

    @Test
    public void importConsumerWithNullUuidOnOwnerShouldSetUuid() throws ImporterException {
        OwnerDTO ownerDTO = mock(OwnerDTO.class);
        Owner owner = mock(Owner.class);
        ConsumerDTO consumer = mock(ConsumerDTO.class);
        ConsumerTypeDTO type = mock(ConsumerTypeDTO.class);

        when(ownerDTO.getId()).thenReturn("test-owner-id");
        when(consumer.getUuid()).thenReturn("test-uuid");
        when(consumer.getOwner()).thenReturn(ownerDTO);
        when(consumer.getType()).thenReturn(type);

        IdentityCertificate idCert = new IdentityCertificate();
        idCert.setSerial(new CertificateSerial());

        importer.store(owner, consumer, new ConflictOverrides(), idCert);

        // now verify that the owner has the upstream consumer set
        ArgumentCaptor<UpstreamConsumer> arg = ArgumentCaptor.forClass(UpstreamConsumer.class);

        verify(owner).setUpstreamConsumer(arg.capture());
        assertEquals("test-uuid", arg.getValue().getUuid());
        verify(curator).merge(owner);
    }

    @Test
    public void importConsumerWithSameUuidOnOwnerShouldDoNothing() throws ImporterException {
        Owner owner = mock(Owner.class);
        OwnerDTO ownerDTO = mock(OwnerDTO.class);
        ConsumerDTO consumer = mock(ConsumerDTO.class);
        ConsumerTypeDTO type = mock(ConsumerTypeDTO.class);
        when(owner.getUpstreamUuid()).thenReturn("test-uuid");
        when(consumer.getUuid()).thenReturn("test-uuid");
        when(consumer.getOwner()).thenReturn(ownerDTO);
        when(consumer.getType()).thenReturn(type);

        IdentityCertificate idCert = new IdentityCertificate();
        idCert.setSerial(new CertificateSerial());

        importer.store(owner, consumer, new ConflictOverrides(), idCert);

        // now verify that the owner didn't change
        // arg.getValue() returns the Owner being stored
        ArgumentCaptor<Owner> arg = ArgumentCaptor.forClass(Owner.class);

        verify(curator).merge(arg.capture());
        assertEquals("test-uuid", arg.getValue().getUpstreamUuid());
    }

    @Test
    public void importConsumerWithSameUuidOnAnotherOwnerShouldThrowException() {
        Owner owner = new Owner();
        UpstreamConsumer uc = new UpstreamConsumer("test-uuid");
        owner.setUpstreamConsumer(uc);
        ConsumerDTO consumer = new ConsumerDTO();
        consumer.setUuid("test-uuid");

        Owner anotherOwner = new Owner()
            .setId("blah")
            .setKey("other")
            .setDisplayName("Other")
            .setUpstreamConsumer(uc);
        when(curator.getByUpstreamUuid(consumer.getUuid())).thenReturn(anotherOwner);

        assertThrows(SyncDataFormatException.class,
            () -> importer.store(owner, consumer, new ConflictOverrides(), null));
    }

    @Test
    public void importConsumerWithMismatchedUuidShouldThrowException() throws ImporterException {
        Owner owner = mock(Owner.class);
        OwnerDTO ownerDTO = mock(OwnerDTO.class);
        ConsumerDTO consumer = mock(ConsumerDTO.class);
        when(owner.getUpstreamUuid()).thenReturn("another-test-uuid");
        when(consumer.getUuid()).thenReturn("test-uuid");
        when(consumer.getOwner()).thenReturn(ownerDTO);

        try {
            importer.store(owner, consumer, new ConflictOverrides(), null);
            fail();
        }
        catch (ImportConflictException e) {
            assertFalse(e.message().getConflicts().isEmpty());
            assertTrue(e.message().getConflicts().contains(
                Importer.Conflict.DISTRIBUTOR_CONFLICT));
        }
    }

    @Test
    public void importConsumerWithMismatchedUuidShouldNotThrowExceptionIfForced() throws ImporterException {
        Owner owner = mock(Owner.class);
        OwnerDTO ownerDTO = mock(OwnerDTO.class);
        ConsumerDTO consumer = mock(ConsumerDTO.class);
        ConsumerTypeDTO type = mock(ConsumerTypeDTO.class);
        when(owner.getUpstreamUuid()).thenReturn("another-test-uuid");
        when(consumer.getUuid()).thenReturn("test-uuid");
        when(consumer.getOwner()).thenReturn(ownerDTO);
        when(consumer.getType()).thenReturn(type);

        IdentityCertificate idCert = new IdentityCertificate();
        idCert.setSerial(new CertificateSerial());

        importer.store(owner, consumer,
            new ConflictOverrides(Importer.Conflict.DISTRIBUTOR_CONFLICT), idCert);

        // now verify that the owner has the upstream consumer set
        ArgumentCaptor<UpstreamConsumer> arg = ArgumentCaptor.forClass(UpstreamConsumer.class);

        verify(owner).setUpstreamConsumer(arg.capture());
        assertEquals("test-uuid", arg.getValue().getUuid());
        verify(curator).merge(owner);
    }

    @Test
    public void importConsumerWithNullUuidOnConsumerShouldThrowException() {
        Owner owner = new Owner();
        ConsumerDTO consumer = new ConsumerDTO();
        consumer.setUuid(null);

        assertThrows(ImporterException.class,
            () -> importer.store(owner, consumer, new ConflictOverrides(), null));
    }

    /*
     * BZ#966860
     */
    @Test
    public void importConsumerWithNullIdCertShouldNotFail() throws ImporterException {
        Owner owner = mock(Owner.class);
        OwnerDTO ownerDTO = mock(OwnerDTO.class);
        ConsumerDTO consumer = mock(ConsumerDTO.class);
        ConsumerTypeDTO type = mock(ConsumerTypeDTO.class);
        when(owner.getUpstreamUuid()).thenReturn("test-uuid");
        when(consumer.getUuid()).thenReturn("test-uuid");
        when(consumer.getOwner()).thenReturn(ownerDTO);
        when(consumer.getType()).thenReturn(type);

        importer.store(owner, consumer, new ConflictOverrides(), null);

        // now verify that the owner has the upstream consumer set
        ArgumentCaptor<UpstreamConsumer> arg = ArgumentCaptor.forClass(UpstreamConsumer.class);

        verify(owner).setUpstreamConsumer(arg.capture());
        assertEquals("test-uuid", arg.getValue().getUuid());
        verify(curator).merge(owner);
    }
}
