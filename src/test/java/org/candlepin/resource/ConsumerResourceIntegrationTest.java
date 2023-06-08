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
package org.candlepin.resource;

import static org.candlepin.test.TestUtil.createConsumerDTO;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobManager;
import org.candlepin.auth.Access;
import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.ContentAccessManager.ContentAccessMode;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.CertificateDTO;
import org.candlepin.dto.api.server.v1.CertificateSerialDTO;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.dto.api.server.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.server.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.server.v1.EntitlementDTO;
import org.candlepin.dto.api.server.v1.HypervisorIdDTO;
import org.candlepin.dto.api.server.v1.OwnerDTO;
import org.candlepin.dto.api.server.v1.ReleaseVerDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.CloudProfileFacts;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Entitlement;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.candlepin.pki.CertificateReader;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.apache.commons.io.FileUtils;
import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;



public class ConsumerResourceIntegrationTest extends DatabaseTestFixture {
    private static final String METADATA_VALUE = "jsontestname";
    private static final String METADATA_NAME = "name";
    private static final String CONSUMER_NAME = "consumer_name";
    private static final String USER_NAME = "testing user";

    @Inject
    private CandlepinPoolManager poolManager;
    @Inject
    private PermissionFactory permFactory;
    @Inject
    private ConsumerResource consumerResource;
    @Inject
    private IdentityCertServiceAdapter icsa;
    @Inject
    private CertificateSerialCurator serialCurator;
    @Inject
    protected ModelTranslator modelTranslator;
    @Inject
    protected JobManager jobManager;

    private ConsumerType standardSystemType;
    private ConsumerTypeDTO standardSystemTypeDTO;
    private ConsumerType personType;
    private ConsumerTypeDTO personTypeDTO;
    private Consumer consumer;
    private Product product;
    private Pool pool;


    private Principal principal;
    private Owner owner;
    private OwnerDTO ownerDTO;
    private Role ownerAdminRole;

    private PrincipalProvider principalProvider;

    private User someuser;

    private static final String DEFAULT_SERVICE_LEVEL = "VIP";

    @Override
    protected Module getGuiceOverrideModule() {
        return new ProductCertCreationModule();
    }

    @BeforeEach
    public void setUp() {
        standardSystemType = consumerTypeCurator.create(new ConsumerType("standard-system"));
        standardSystemTypeDTO = modelTranslator.translate(standardSystemType, ConsumerTypeDTO.class);
        personType = consumerTypeCurator.create(new ConsumerType(ConsumerTypeEnum.PERSON));
        personTypeDTO = modelTranslator.translate(personType, ConsumerTypeDTO.class);

        this.owner = new Owner()
            .setKey("test-owner")
            .setDisplayName("test-owner")
            .setDefaultServiceLevel(DEFAULT_SERVICE_LEVEL);

        // Many of these tests were written before the conception of SCA mode, so explicitly set the
        // shared owners's CA mode to entitlement. In the future, the tests should be updated to
        // (a) not use shared data like this, and (b) be explicit about the operating mode necessary
        // for testing a given unit.
        this.owner.setContentAccessModeList(ContentAccessMode.ENTITLEMENT.toDatabaseValue())
            .setContentAccessMode(ContentAccessMode.ENTITLEMENT.toDatabaseValue());

        this.owner = this.ownerCurator.create(owner);

        this.ownerDTO = modelTranslator.translate(owner, OwnerDTO.class);

        this.principalProvider = mock(PrincipalProvider.class);
        someuser = userCurator.create(new User(USER_NAME, "dontcare"));

        ownerAdminRole = createAdminRole(owner);
        ownerAdminRole.addUser(someuser);
        roleCurator.create(ownerAdminRole);

        Collection<Permission> perms = permFactory.createPermissions(someuser,
            ownerAdminRole.getPermissions());

        principal = new UserPrincipal(USER_NAME, perms, false);
        setupPrincipal(principal);

        consumer = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(consumer);

        product = TestUtil.createProduct();
        product.setAttribute(Product.Attributes.SUPPORT_LEVEL, DEFAULT_SERVICE_LEVEL);
        productCurator.create(product);

        pool = createPool(owner, product, 10L,
            TestUtil.createDate(2010, 1, 1), TestUtil.createDateOffset(10, 0, 0));
    }

    @AfterEach
    public void cleanup() {
        // cleanup the temp exports
        File tempDir = new File("/tmp");

        for (File f : tempDir.listFiles()) {
            if (f.isDirectory() && f.getName().startsWith("export")) {
                try {
                    FileUtils.deleteDirectory(f);
                }
                catch (IOException e) {
                    throw new RuntimeException(
                        "Failed to cleanup directory: " + "/tmp", e);
                }
            }
        }
    }

    @Test
    public void testGetCerts() {
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, 1, null, null, false, null, null);
        List<CertificateDTO> serials = consumerResource.getEntitlementCertificates(consumer.getUuid(), null);
        assertEquals(1, serials.size());
    }

    @Test
    public void testGetSerialFiltering() {
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, 1, null, null, false, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, 1, null, null, false, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, 1, null, null, false, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, 1, null, null, false, null, null);
        List<CertificateDTO> certificates = consumerResource
            .getEntitlementCertificates(consumer.getUuid(), null);
        assertEquals(4, certificates.size());

        Long serial1 = certificates.get(0).getSerial().getId();
        Long serial2 = certificates.get(3).getSerial().getId();

        String serialsToFilter = serial1 + "," + serial2;

        certificates = consumerResource.getEntitlementCertificates(consumer.getUuid(), serialsToFilter);
        assertEquals(2, certificates.size());
        assertEquals(serial1, certificates.get(0).getSerial().getId());
        assertEquals(serial2, certificates.get(1).getSerial().getId());
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void testCreateConsumer() {
        ConsumerDTO toSubmit = createConsumerDTO(CONSUMER_NAME, USER_NAME, null,
            standardSystemTypeDTO);
        toSubmit.putFactsItem(METADATA_NAME, METADATA_VALUE);
        ConsumerDTO submitted = consumerResource.createConsumer(
            toSubmit,
            someuser.getUsername(),
            owner.getKey(), null, true);

        assertNotNull(submitted);
        assertNotNull(consumerCurator.get(submitted.getId()));
        assertEquals(standardSystemType.getLabel(), submitted.getType().getLabel());
        assertEquals(METADATA_VALUE, consumerResource.getFactValue(submitted.getFacts(), METADATA_NAME));
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void testCreateConsumerVsDefaultServiceLevelForOwner() {
        ConsumerDTO toSubmit = createConsumerDTO(CONSUMER_NAME, USER_NAME, null, standardSystemTypeDTO);
        ConsumerDTO submitted = consumerResource.createConsumer(
            toSubmit,
            someuser.getUsername(),
            owner.getKey(), null, true);

        assertEquals(DEFAULT_SERVICE_LEVEL, submitted.getServiceLevel());

    }

    @Test
    public void testCreateConsumerWithUUID() {
        String uuid = "Jar Jar Binks";
        ConsumerDTO toSubmit = createConsumerDTO(CONSUMER_NAME, USER_NAME, null, standardSystemTypeDTO);
        assertNull(toSubmit.getId());
        toSubmit.setUuid(uuid);
        toSubmit.putFactsItem(METADATA_NAME, METADATA_VALUE);

        ConsumerDTO submitted = consumerResource.createConsumer(toSubmit, null, owner.getKey(), null,
            true);
        assertNotNull(submitted);
        assertNotNull(submitted.getId());
        assertNotNull(consumerCurator.get(submitted.getId()));
        assertNotNull(consumerCurator.findByUuid(uuid));
        assertEquals(standardSystemType.getLabel(), submitted.getType().getLabel());
        assertEquals(METADATA_VALUE, consumerResource.getFactValue(submitted.getFacts(), METADATA_NAME));
        assertEquals(uuid, submitted.getUuid());

        // The second post should fail because of constraint failures
        ConsumerDTO anotherToSubmit = createConsumerDTO(CONSUMER_NAME, USER_NAME, null,
            standardSystemTypeDTO);
        anotherToSubmit.setUuid(uuid);
        anotherToSubmit.putFactsItem(METADATA_NAME, METADATA_VALUE);
        anotherToSubmit.setId(null);
        assertThrows(BadRequestException.class, () ->
            consumerResource.createConsumer(anotherToSubmit, null, owner.getKey(), null, true));
    }

    public static Stream<Arguments> manifestConsumerContentAccessModeInputSource() {
        String entMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String scaMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();
        String combined = String.join(",", entMode, scaMode);

        return Stream.of(
            Arguments.of(entMode, entMode, entMode, entMode, true),
            Arguments.of(entMode, entMode, scaMode, entMode, false),
            Arguments.of(entMode, entMode, "potato", entMode, false),
            Arguments.of(entMode, entMode, "", null, true),
            Arguments.of(entMode, entMode, null, entMode, true),

            Arguments.of(scaMode, scaMode, entMode, scaMode, false),
            Arguments.of(scaMode, scaMode, scaMode, scaMode, true),
            Arguments.of(scaMode, scaMode, "potato", scaMode, false),
            Arguments.of(scaMode, scaMode, "", null, true),
            Arguments.of(scaMode, scaMode, null, scaMode, true),

            Arguments.of(combined, entMode, entMode, entMode, true),
            Arguments.of(combined, entMode, scaMode, scaMode, true),
            Arguments.of(combined, entMode, "potato", scaMode, false),
            Arguments.of(combined, entMode, "", null, true),
            Arguments.of(combined, entMode, null, scaMode, true),

            Arguments.of(combined, scaMode, entMode, entMode, true),
            Arguments.of(combined, scaMode, scaMode, scaMode, true),
            Arguments.of(combined, scaMode, "potato", null, false),
            Arguments.of(combined, scaMode, "", null, true),
            Arguments.of(combined, scaMode, null, scaMode, true)
        );
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("manifestConsumerContentAccessModeInputSource")
    public void testManifestConsumerCreationContentAccessMode(String ownerModeList, String ownerMode,
        String providedContentAccessMode, String expectedConsumerMode, boolean validInputs) {

        this.owner.setContentAccessModeList(ownerModeList);
        this.owner.setContentAccessMode(ownerMode);

        ConsumerType manifestType = new ConsumerType("manifest");
        manifestType.setManifest(true);
        manifestType = this.consumerTypeCurator.create(manifestType);
        ConsumerTypeDTO manifestTypeDTO = modelTranslator.translate(manifestType, ConsumerTypeDTO.class);

        ConsumerDTO dto = createConsumerDTO(CONSUMER_NAME, USER_NAME, null, manifestTypeDTO);
        dto.setContentAccessMode(providedContentAccessMode);

        if (validInputs) {
            ConsumerDTO output = this.consumerResource
                .createConsumer(dto, null, this.owner.getKey(), null, false);

            assertNotNull(output);
            assertEquals(expectedConsumerMode, output.getContentAccessMode());
        }
        else {
            assertThrows(BadRequestException.class, () ->
                this.consumerResource.createConsumer(dto, null, this.owner.getKey(), null, false));
        }
    }

    public static Stream<Arguments> systemConsumerContentAccessModeInputSource() {
        String entMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String scaMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        return Stream.of(
            Arguments.of(entMode, null, false),
            Arguments.of(scaMode, null, false),
            Arguments.of("potato", null, false),
            Arguments.of("", null, true),
            Arguments.of(null, null, true)
        );
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("systemConsumerContentAccessModeInputSource")
    public void testSystemConsumerCreationContentAccessMode(String providedConsumerMode,
        String expectedConsumerMode, boolean validInputs) {

        ConsumerDTO dto = createConsumerDTO(CONSUMER_NAME, USER_NAME, null, this.standardSystemTypeDTO);
        dto.setContentAccessMode(providedConsumerMode);

        if (validInputs) {
            ConsumerDTO output = this.consumerResource
                .createConsumer(dto, null, this.owner.getKey(), null, false);

            assertNotNull(output);
            assertNull(output.getContentAccessMode());
        }
        else {
            assertThrows(BadRequestException.class, () ->
                this.consumerResource.createConsumer(dto, null, this.owner.getKey(), null, false));
        }
    }

    @Test
    @Disabled
    public void testDeleteResource() {
        Consumer created = consumerCurator.create(new Consumer()
            .setName(CONSUMER_NAME)
            .setUsername(USER_NAME)
            .setOwner(owner)
            .setType(standardSystemType));
        consumerResource.deleteConsumer(consumer.getUuid());

        assertNull(consumerCurator.get(created.getId()));
    }

    @Test
    public void testUsername() {
        // not setting the username here - this should be set by
        // examining the user principal
        ConsumerDTO consumer = createConsumerDTO("random-consumer", null, null, standardSystemTypeDTO);
        consumer = consumerResource.createConsumer(consumer, null, null, null, true);

        assertEquals(USER_NAME, consumer.getUsername());
    }

    @Test
    public void testReadOnlyUsersCanGenerateExports() {
        // add an identity certificate for the export
        IdentityCertificate idCert = TestUtil.createIdCert();
        idCert.setId(null); // needs to be null to persist
        certSerialCurator.create(idCert.getSerial());
        identityCertificateCurator.create(idCert);
        consumer.setIdCert(idCert);

        consumer.setType(consumerTypeCurator.create(new ConsumerType(ConsumerTypeEnum.CANDLEPIN)));
        consumerCurator.update(consumer);
        setupPrincipal(owner, Access.READ_ONLY);
        securityInterceptor.enable();
        ResteasyContext.pushContext(HttpServletResponse.class, mock(HttpServletResponse.class));
        consumerResource.exportData(consumer.getUuid(), null, null, null);
        // if no exception, we're good
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBindByPool() {
        Response rsp = consumerResource.bind(
            consumer.getUuid(), pool.getId(), null, 1, null,
            null, false, null, null);

        List<EntitlementDTO> resultList = (List<EntitlementDTO>) rsp.getEntity();

        consumer = consumerCurator.findByUuid(consumer.getUuid());
        assertEquals(1, consumer.getEntitlements().size());

        pool = poolManager.get(pool.getId());
        assertEquals(Long.valueOf(1), pool.getConsumed());
        assertEquals(1, resultList.size());
        assertEquals(pool.getId(), resultList.get(0).getPool().getId());

        Entitlement ent = new Entitlement();
        ent.setId(resultList.get(0).getId());

        assertEquals(1, entitlementCertificateCurator.listForEntitlement(ent).size());
    }

    @Test
    public void testRegisterWithConsumerId() {
        ConsumerDTO toSubmit = createConsumerDTO(CONSUMER_NAME, USER_NAME, null, standardSystemTypeDTO);
        toSubmit.setUuid("1023131");
        toSubmit.putFactsItem(METADATA_NAME, METADATA_VALUE);

        ConsumerDTO submitted = consumerResource.createConsumer(toSubmit, null, null, null, true);

        assertNotNull(submitted);
        assertEquals(toSubmit.getUuid(), submitted.getUuid());
        assertNotNull(consumerCurator.get(submitted.getId()));
        assertEquals(standardSystemType.getLabel(), submitted.getType().getLabel());
        assertEquals(METADATA_VALUE, consumerResource.getFactValue(submitted.getFacts(), METADATA_NAME));

        // now pass in consumer type with null id just like the client would
        ConsumerTypeDTO type = new ConsumerTypeDTO()
            .label(standardSystemType.getLabel());

        assertNull(type.getId());
        ConsumerDTO nulltypeid = createConsumerDTO(CONSUMER_NAME, USER_NAME, null, type);
        submitted = consumerResource.createConsumer(
            nulltypeid,
            null, null, null, true);
        assertNotNull(submitted);
        assertNotNull(submitted.getUuid());
        assertNotNull(submitted.getType().getId());
    }

    @Test
    public void unbindBySerialWithExistingCertificateShouldPass() {
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);
        List<CertificateDTO> serials = consumerResource
            .getEntitlementCertificates(consumer.getUuid(), null);
        assertEquals(1, serials.size());

        consumerResource.unbindBySerial(consumer.getUuid(), Long.valueOf(serials.get(0).getSerial().getId()));
        assertEquals(0, consumerResource.listEntitlements(
            consumer.getUuid(), null, true, new ArrayList<>(), null, null, null, null).size());
    }

    @Test
    public void testCannotGetAnotherConsumersCerts() {
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);

        Consumer evilConsumer = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(evilConsumer);
        setupPrincipal(new ConsumerPrincipal(evilConsumer, owner));

        securityInterceptor.enable();

        assertThrows(NotFoundException.class, () ->
            consumerResource.getEntitlementCertificates(consumer.getUuid(), null)
        );
    }

    @Test
    public void testCanGetOwnedConsumersCerts() {
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);
        setupPrincipal(new ConsumerPrincipal(consumer, owner));

        assertEquals(3, consumerResource.getEntitlementCertificates(consumer.getUuid(), null).size());
    }

    @Test
    public void canNotDeleteConsumerOtherThanSelf() {
        Consumer evilConsumer = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(evilConsumer);
        Principal p = setupPrincipal(new ConsumerPrincipal(evilConsumer, owner));

        securityInterceptor.enable();
        when(this.principalProvider.get()).thenReturn(p);
        assertThrows(NotFoundException.class, () ->
            consumerResource.deleteConsumer(consumer.getUuid())
        );
    }

    @Test
    public void consumerCanDeleteSelf() throws GeneralSecurityException, IOException {
        Consumer toSubmit = new Consumer()
            .setName(CONSUMER_NAME)
            .setUsername(USER_NAME)
            .setOwner(owner)
            .setType(standardSystemType)
            .setFact(METADATA_NAME, METADATA_VALUE);

        Consumer c = consumerCurator.create(toSubmit);

        IdentityCertificate idCert = icsa.generateIdentityCert(c);
        c.setIdCert(idCert);
        setupPrincipal(new ConsumerPrincipal(c, owner));
        consumerResource.deleteConsumer(c.getUuid());
    }

    @Test
    public void getConsumersCerts() {
        setupAdminPrincipal("admin");
        securityInterceptor.enable();

        assertEquals(0, consumerResource.getEntitlementCertificates(
            consumer.getUuid(), null).size());
    }

    @Test
    public void testCannotGetAnotherOwnersConsumersCerts() {
        Consumer evilConsumer = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(evilConsumer);

        Owner evilOwner = ownerCurator.create(new Owner("another-owner"));
        ownerCurator.create(evilOwner);

        setupPrincipal(evilOwner, Access.ALL);
        securityInterceptor.enable();

        assertThrows(NotFoundException.class, () ->
            consumerResource.getEntitlementCertificates(consumer.getUuid(), null));
    }

    @Test
    public void testConsumerCannotListAllConsumers() {
        setupPrincipal(new ConsumerPrincipal(consumer, owner));
        securityInterceptor.enable();

        assertThrows(ForbiddenException.class, () -> consumerResource
            .searchConsumers(null, null, null, new ArrayList<>(), null, null, null, null, null, null));
    }

    @Test
    public void consumerShouldSeeOwnEntitlements() {
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);

        setupPrincipal(new ConsumerPrincipal(consumer, owner));
        securityInterceptor.enable();

        assertEquals(3, consumerResource.listEntitlements(
            consumer.getUuid(), null, true, new ArrayList<>(), null, null, null, null).size());
    }

    @Test
    public void consumerShouldNotSeeAnotherConsumersEntitlements() {
        Consumer evilConsumer = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(evilConsumer);

        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);
        consumerResource.bind(evilConsumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);

        setupPrincipal(new ConsumerPrincipal(evilConsumer, owner));
        securityInterceptor.enable();

        assertThrows(NotFoundException.class, () -> consumerResource.listEntitlements(consumer.getUuid(),
            null, true, new ArrayList<>(), null, null, null, null)
        );
    }

    @Test
    public void ownerShouldNotSeeOtherOwnerEntitlements() {
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);

        Owner evilOwner = ownerCurator.create(new Owner("another-owner"));
        ownerCurator.create(evilOwner);

        securityInterceptor.enable();
        setupPrincipal(evilOwner, Access.ALL);

        assertThrows(NotFoundException.class, () -> consumerResource.listEntitlements(consumer.getUuid(),
            null, true, new ArrayList<>(), null, null, null, null)
        );
    }

    @Test
    public void ownerShouldSeeOwnEntitlements() {
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, 1, null, null, false, null, null);
        securityInterceptor.enable();

        assertEquals(3, consumerResource.listEntitlements(
            consumer.getUuid(), null, true, new ArrayList<>(), null, null, null, null).size());
    }

    @Test
    public void personalNameOverride() {
        ConsumerDTO personal = createConsumerDTO(personTypeDTO, ownerDTO);

        personal = consumerResource.createConsumer(personal, null, null, null, true);

        // Not sure if this should be hard-coded to default
        assertEquals(USER_NAME, personal.getName());
    }

    @Test
    public void userWithEmail() {
        String username = "(foo)@{baz}.[com]&?";
        User u = userCurator.create(new User(username, "dontcare"));
        ownerAdminRole.addUser(u);
        roleCurator.merge(ownerAdminRole);

        Principal emailuser = TestUtil.createPrincipal(username, owner, Access.ALL);
        setupPrincipal(emailuser);

        ConsumerDTO personal = createConsumerDTO(personTypeDTO, ownerDTO);
        personal.setName(emailuser.getUsername());

        personal = consumerResource.createConsumer(personal, username, null, null, true);

        // Not sure if this should be hard-coded to default
        assertEquals(username, personal.getName());
    }

    @Test
    public void onlyOnePersonalConsumer() {
        ConsumerDTO personal = createConsumerDTO(personTypeDTO, ownerDTO);
        consumerResource.createConsumer(personal, null, null, null, true);

        personal = createConsumerDTO(personTypeDTO, ownerDTO);
        ConsumerDTO finalPersonal = personal;
        assertThrows(BadRequestException.class, () ->
            consumerResource.createConsumer(finalPersonal, null, null, null, true)
        );
    }

    /**
     * Test verifies that list of certs changes after regeneration
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testRegenerateEntitlementCertificateWithValidConsumerByEntitlement() {
        Response rsp = consumerResource.bind(consumer.getUuid(), pool.getId(), null, 1, null,
            null, false, null, null);

        List<EntitlementDTO> resultList = (List<EntitlementDTO>) rsp.getEntity();
        EntitlementDTO ent = resultList.get(0);
        assertEquals(1, ent.getCertificates().size());
        CertificateDTO entCertBefore = ent.getCertificates().iterator().next();

        consumerResource.regenerateEntitlementCertificates(this.consumer.getUuid(), ent.getId(), false,
            false);

        Entitlement entWithRefreshedCerts = entitlementCurator.get(ent.getId());
        ent = this.modelTranslator.translate(entWithRefreshedCerts, EntitlementDTO.class);
        assertEquals(1, ent.getCertificates().size());
        CertificateDTO entCertAfter = ent.getCertificates().iterator().next();

        assertNotEquals(entCertBefore, entCertAfter);
    }

    @Test
    public void testInvalidProductId() {
        assertThrows(BadRequestException.class, () -> consumerResource.bind(consumer.getUuid(), "JarjarBinks",
            null, null, null, null, false, null, null)
        );
    }

    private static class ProductCertCreationModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(Configuration.class).toInstance(TestConfig.defaults());
            bind(CertificateReader.class).asEagerSingleton();
        }
    }

    @Test
    public void testContentAccessExpireRegen() {
        owner.setContentAccessModeList(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());
        owner.setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());
        ownerCurator.merge(owner);

        consumer = TestUtil.createConsumer(standardSystemType, owner);
        consumer.setFact("system.certificate_version", "3.3");
        consumerCurator.create(consumer);

        List<CertificateDTO> serials = consumerResource.getEntitlementCertificates(consumer.getUuid(), null);
        assertEquals(1, serials.size());

        CertificateDTO original = serials.get(0);
        CertificateSerialDTO serialDTO = original.getSerial();
        CertificateSerial serial = new CertificateSerial(serialDTO.getId(),
            Util.toDate(serialDTO.getExpiration()));
        serial.setSerial(serialDTO.getSerial());
        serial.setRevoked(serialDTO.getRevoked());

        Calendar cal = Calendar.getInstance();
        cal.setTime(serial.getExpiration());
        cal.add(Calendar.YEAR, -2);
        serial.setExpiration(cal.getTime());
        serialCurator.merge(serial);

        serials = consumerResource.getEntitlementCertificates(consumer.getUuid(), null);
        assertEquals(1, serials.size());
        CertificateDTO updated = serials.get(0);
        assertThat(updated, instanceOf(CertificateDTO.class));
        assertNotEquals(original.getSerial().getId(), updated.getSerial().getId());
    }

    @Test
    public void testCloudProfileUpdatedOnConsumerSysRoleUpdate() {
        ConsumerDTO consumer = createConsumerDTO("random-consumer", null, null, standardSystemTypeDTO);
        consumer = consumerResource.createConsumer(consumer, null, null, null, true);

        consumer.setRole("test-role");
        consumerResource.updateConsumer(consumer.getUuid(), consumer);
        Date profileModified = consumerCurator.get(consumer.getId()).getRHCloudProfileModified();

        assertNotNull(consumerCurator.get(consumer.getId()).getRHCloudProfileModified());

        consumer.setRole("update-role");
        consumerResource.updateConsumer(consumer.getUuid(), consumer);
        Date updatedProfileModified = consumerCurator.get(consumer.getId()).getRHCloudProfileModified();

        assertNotNull(consumerCurator.get(consumer.getId()).getRHCloudProfileModified());
        assertNotEquals(profileModified, updatedProfileModified);
    }

    @Test
    public void testCloudProfileUpdatedOnInstalledProductsUpdate() {
        ConsumerDTO consumer = createConsumerDTO("random-consumer", null, null, standardSystemTypeDTO);
        consumer = consumerResource.createConsumer(consumer, null, null, null, true);

        Date beforeUpdateTimestamp = consumerCurator.findByUuid(consumer.getUuid())
            .getRHCloudProfileModified();

        Product prod = TestUtil.createProduct("Product One");

        ConsumerInstalledProductDTO updatedInstalledProduct = new ConsumerInstalledProductDTO()
            .productId(prod.getId())
            .productName(prod.getName());

        consumer.setInstalledProducts(Set.of(updatedInstalledProduct));

        consumerResource.updateConsumer(consumer.getUuid(), consumer);

        Date afterUpdateTimestamp = consumerCurator.findByUuid(consumer.getUuid())
            .getRHCloudProfileModified();

        assertNotNull(afterUpdateTimestamp);
        assertNotEquals(beforeUpdateTimestamp, afterUpdateTimestamp);
    }

    @Test
    public void testCloudProfileUpdatedOnHypervisorUpdate() {
        ConsumerDTO consumer = createConsumerDTO("random-consumer", null, null, standardSystemTypeDTO);
        consumer = consumerResource.createConsumer(consumer, null, null, null, true);

        Date beforeUpdateTimestamp = consumerCurator.findByUuid(consumer.getUuid())
            .getRHCloudProfileModified();

        HypervisorIdDTO updatedHypervisorId = new HypervisorIdDTO();
        consumer.setHypervisorId(updatedHypervisorId);
        consumerResource.updateConsumer(consumer.getUuid(), consumer);

        Date afterUpdateTimestamp = consumerCurator.findByUuid(consumer.getUuid())
            .getRHCloudProfileModified();

        assertNotNull(afterUpdateTimestamp);
        assertNotEquals(beforeUpdateTimestamp, afterUpdateTimestamp);
    }

    @Test
    public void testCloudProfileUpdatedOnSpecificConsumerFactsUpdate() {
        ConsumerDTO consumer = createConsumerDTO("random-consumer", null, null, standardSystemTypeDTO);
        consumer = consumerResource.createConsumer(consumer, null, null, null, true);
        Date modifiedDateOnCreate = consumerCurator.get(consumer.getId()).getRHCloudProfileModified();

        ConsumerDTO updatedConsumerDTO = new ConsumerDTO();
        updatedConsumerDTO.putFactsItem("FACT", "FACT_VALUE");
        consumerResource.updateConsumer(consumer.getUuid(), updatedConsumerDTO);
        Date modifiedTSOnUnnecessaryFactUpdate = consumerCurator.get(consumer.getId())
            .getRHCloudProfileModified();

        assertEquals(modifiedDateOnCreate, modifiedTSOnUnnecessaryFactUpdate);

        updatedConsumerDTO = new ConsumerDTO();
        updatedConsumerDTO.putFactsItem(CloudProfileFacts.CPU_CORES_PER_SOCKET.getFact(), "1");
        consumerResource.updateConsumer(consumer.getUuid(), updatedConsumerDTO);
        Date modifiedTSOnNecessaryFactUpdate = consumerCurator.get(consumer.getId())
            .getRHCloudProfileModified();

        assertNotNull(consumerCurator.get(consumer.getId()).getRHCloudProfileModified());
        assertNotEquals(modifiedTSOnUnnecessaryFactUpdate, modifiedTSOnNecessaryFactUpdate);
    }

    @Test
    public void testCloudProfileNotUpdatedOnConsumerUpdates() {
        ConsumerDTO consumer = createConsumerDTO("random-consumer", null, null, standardSystemTypeDTO);
        consumer = consumerResource.createConsumer(consumer, null, null, null, true);
        Date profileCreated = consumerCurator.get(consumer.getId()).getRHCloudProfileModified();

        // Impl note:
        // The generated DTOs blindly pass through any collections, which means we could end up with
        // an immutable collection here if we don't explicitly rebox them.
        Map<String, String> updatedFacts = new HashMap<>(consumer.getFacts());
        updatedFacts.put("lscpu.model", "78");

        consumer.setAutoheal(true);
        consumer.setSystemPurposeStatus("test-status");
        consumer.setReleaseVer(new ReleaseVerDTO().releaseVer("test-release-version"));
        consumer.setFacts(updatedFacts);

        consumerResource.updateConsumer(consumer.getUuid(), consumer);
        Date profileModified = consumerCurator.get(consumer.getId()).getRHCloudProfileModified();

        assertEquals(profileCreated, profileModified);
    }

    @Test
    public void testCloudProfileNotUpdatedOnNonCloudFactUpdates() {
        ConsumerDTO consumer = createConsumerDTO("random-consumer", null, null, standardSystemTypeDTO);
        consumer = consumerResource.createConsumer(consumer, null, null, null, true);
        Date profileCreated = consumerCurator.get(consumer.getId()).getRHCloudProfileModified();

        // Impl note:
        // The generated DTOs blindly pass through any collections, which means we could end up with
        // an immutable collection here if we don't explicitly rebox them.
        Map<String, String> updatedFacts = new HashMap<>(consumer.getFacts());
        updatedFacts.put("lscpu.model", "78");
        updatedFacts.put("test-dmi.bios.vendor", "vendorA");

        consumer.setFacts(updatedFacts);

        consumerResource.updateConsumer(consumer.getUuid(), consumer);
        Date profileModified = consumerCurator.get(consumer.getId()).getRHCloudProfileModified();

        assertEquals(profileCreated, profileModified);
    }
}
