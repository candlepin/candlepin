/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import org.candlepin.auth.Access;
import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Role;
import org.candlepin.model.RoleCurator;
import org.candlepin.model.User;
import org.candlepin.model.UserCurator;
import org.candlepin.pki.PKIReader;
import org.candlepin.pki.impl.BouncyCastlePKIReader;
import org.candlepin.resource.util.ConsumerBindUtil;
import org.candlepin.resteasy.parameter.KeyValueParameter;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestDateUtil;
import org.candlepin.test.TestUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

/**
 * ConsumerResourceTest
 */
public class ConsumerResourceIntegrationTest extends DatabaseTestFixture {
    private static final String METADATA_VALUE = "jsontestname";
    private static final String METADATA_NAME = "name";
    private static final String CONSUMER_NAME = "consumer_name";
    private static final String USER_NAME = "testing user";

    @Inject private OwnerCurator ownerCurator;
    @Inject private UserCurator userCurator;
    @Inject private ProductCurator productCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private EntitlementCurator entitlementCurator;
    @Inject private EntitlementCertificateCurator entCertCurator;
    @Inject private RoleCurator roleCurator;
    @Inject private CertificateSerialCurator certSerialCurator;
    @Inject private CandlepinPoolManager poolManager;
    @Inject private PermissionFactory permFactory;
    @Inject private ConsumerResource consumerResource;
    @Inject private IdentityCertificateCurator idCurator;
    @Inject private IdentityCertServiceAdapter icsa;


    private ConsumerType standardSystemType;
    private ConsumerType personType;
    private Consumer consumer;
    private Product product;
    private Pool pool;

    private Principal principal;
    private Owner owner;
    private Role ownerAdminRole;

    private User someuser;

    private static final String DEFAULT_SERVICE_LEVEL = "VIP";

    @Override
    protected Module getGuiceOverrideModule() {
        return new ProductCertCreationModule();
    }

    @Before
    public void setUp() {
        standardSystemType = consumerTypeCurator.create(new ConsumerType(
            "standard-system"));

        personType = consumerTypeCurator.create(new ConsumerType(
            ConsumerTypeEnum.PERSON));
        owner = ownerCurator.create(new Owner("test-owner"));
        owner.setDefaultServiceLevel(DEFAULT_SERVICE_LEVEL);
        ownerCurator.create(owner);

        someuser = userCurator.create(new User(USER_NAME, "dontcare"));

        ownerAdminRole = createAdminRole(owner);
        ownerAdminRole.addUser(someuser);
        roleCurator.create(ownerAdminRole);

        List<Permission> perms = permFactory.createPermissions(someuser,
            ownerAdminRole.getPermissions());
        principal = new UserPrincipal(USER_NAME, perms, false);
        setupPrincipal(principal);

        consumer = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(consumer);

        product = TestUtil.createProduct(owner);
        product.addAttribute(new ProductAttribute("support_level", DEFAULT_SERVICE_LEVEL));
        productCurator.create(product);

        pool = createPool(owner, product, 10L,
            TestDateUtil.date(2010, 1, 1), TestDateUtil.date(2020, 12, 31));
    }

    @After
    public void cleanup() {
        // cleanup the temp exports
        TestUtil.cleanupDir("/tmp", "export");
    }

    @Test
    public void testGetCerts() {
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        List<EntitlementCertificate> serials = consumerResource
            .getEntitlementCertificates(consumer.getUuid(), null);
        assertEquals(1, serials.size());
    }

    @Test
    public void testGetSerialFiltering() {
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        List<EntitlementCertificate> certificates = consumerResource
            .getEntitlementCertificates(consumer.getUuid(), null);
        assertEquals(4, certificates.size());

        Long serial1 = Long.valueOf(certificates.get(0).getSerial().getId());
        Long serial2 = Long.valueOf(certificates.get(3).getSerial().getId());

        String serialsToFilter = serial1.toString() + "," + serial2.toString();

        certificates = consumerResource.getEntitlementCertificates(
            consumer.getUuid(), serialsToFilter);
        assertEquals(2, certificates.size());
        assertEquals(serial1, certificates.get(0).getSerial().getId());
        assertEquals(serial2, certificates.get(1).getSerial().getId());
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void testCreateConsumer() {
        Consumer toSubmit = new Consumer(CONSUMER_NAME, USER_NAME, null,
            standardSystemType);
        toSubmit.getFacts().put(METADATA_NAME, METADATA_VALUE);
        Consumer submitted = consumerResource.create(
            toSubmit,
            new UserPrincipal(someuser.getUsername(), Arrays.asList(new Permission [] {
                new OwnerPermission(owner, Access.ALL) }), false),
            someuser.getUsername(),
            owner.getKey(), null, true);

        assertNotNull(submitted);
        assertNotNull(consumerCurator.find(submitted.getId()));
        assertEquals(standardSystemType.getLabel(), submitted.getType()
            .getLabel());
        assertEquals(METADATA_VALUE, submitted.getFact(METADATA_NAME));
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void testCreateConsumerVsDefaultServiceLevelForOwner() {
        Consumer toSubmit = new Consumer(CONSUMER_NAME, USER_NAME, null,
            standardSystemType);
        Consumer submitted = consumerResource.create(
            toSubmit,
            new UserPrincipal(someuser.getUsername(), Arrays.asList(new Permission [] {
                new OwnerPermission(owner, Access.ALL) }), false),
            someuser.getUsername(),
            owner.getKey(), null, true);

        assertEquals(DEFAULT_SERVICE_LEVEL, submitted.getServiceLevel());

    }

    @Test(expected = BadRequestException.class)
    public void testCreateConsumerWithUUID() {
        String uuid = "Jar Jar Binks";
        Consumer toSubmit = new Consumer(CONSUMER_NAME, USER_NAME, null,
            standardSystemType);
        assertNull(toSubmit.getId());
        toSubmit.setUuid(uuid);
        toSubmit.getFacts().put(METADATA_NAME, METADATA_VALUE);

        Consumer submitted = consumerResource.create(toSubmit, principal, null,
            owner.getKey(), null, true);
        assertNotNull(submitted);
        assertNotNull(submitted.getId());
        assertNotNull(consumerCurator.find(submitted.getId()));
        assertNotNull(consumerCurator.findByUuid(uuid));
        assertEquals(standardSystemType.getLabel(), submitted.getType()
            .getLabel());
        assertEquals(METADATA_VALUE, submitted.getFact(METADATA_NAME));
        assertEquals("The Uuids do not match", uuid, submitted.getUuid());

        // The second post should fail because of constraint failures
        Consumer anotherToSubmit = new Consumer(CONSUMER_NAME, USER_NAME, null,
            standardSystemType);
        anotherToSubmit.setUuid(uuid);
        anotherToSubmit.getFacts().put(METADATA_NAME, METADATA_VALUE);
        anotherToSubmit.setId(null);
        consumerResource.create(anotherToSubmit, principal, null, owner.getKey(), null, true);
    }

    public void testDeleteResource() {
        Consumer created = consumerCurator.create(new Consumer(CONSUMER_NAME,
            USER_NAME, owner, standardSystemType));
        consumerResource.deleteConsumer(consumer.getUuid(), principal);

        assertNull(consumerCurator.find(created.getId()));
    }

    @Test
    public void testUsername() throws IOException, GeneralSecurityException {

        // not setting the username here - this should be set by
        // examining the user principal
        Consumer consumer = new Consumer("random-consumer", null, null,
            standardSystemType);

        consumer = consumerResource.create(consumer, principal, null, null,
            null, true);

        assertEquals(USER_NAME, consumer.getUsername());
    }

    @Test
    public void testReadOnlyUsersCanGenerateExports() {
        // add an identity certificate for the export
        IdentityCertificate idCert = TestUtil.createIdCert();
        idCert.setId(null); // needs to be null to persist
        idCert.getSerial().setId(null);  // needs to be null to persist
        certSerialCurator.create(idCert.getSerial());
        idCurator.create(idCert);
        consumer.setIdCert(idCert);

        consumer.setType(consumerTypeCurator.create(
            new ConsumerType(ConsumerTypeEnum.CANDLEPIN)));
        consumerCurator.update(consumer);
        setupPrincipal(owner, Access.READ_ONLY);
        securityInterceptor.enable();
        consumerResource.exportData(mock(HttpServletResponse.class),
            consumer.getUuid(), null, null, null);
        // if no exception, we're good
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBindByPool() throws Exception {
        Response rsp = consumerResource.bind(
            consumer.getUuid(), pool.getId().toString(), null, 1, null,
            null, false, null, null, null, null);

        List<Entitlement> resultList = (List<Entitlement>) rsp.getEntity();

        consumer = consumerCurator.findByUuid(consumer.getUuid());
        assertEquals(1, consumer.getEntitlements().size());

        pool = poolManager.find(pool.getId());
        assertEquals(Long.valueOf(1), pool.getConsumed());
        assertEquals(1, resultList.size());
        assertEquals(pool.getId(), resultList.get(0).getPool().getId());
        assertEquals(1, entCertCurator.listForEntitlement(resultList.get(0))
            .size());
    }

    @Test
    public void testRegisterWithConsumerId() {
        Consumer toSubmit = new Consumer(CONSUMER_NAME, USER_NAME, null,
            standardSystemType);
        toSubmit.setUuid("1023131");
        toSubmit.getFacts().put(METADATA_NAME, METADATA_VALUE);

        Consumer submitted = consumerResource.create(
            toSubmit,
            TestUtil.createPrincipal(someuser.getUsername(), owner, Access.ALL),
            null, null, null, true);

        assertNotNull(submitted);
        assertEquals(toSubmit.getUuid(), submitted.getUuid());
        assertNotNull(consumerCurator.find(submitted.getId()));
        assertEquals(standardSystemType.getLabel(), submitted.getType()
            .getLabel());
        assertEquals(METADATA_VALUE, submitted.getFact(METADATA_NAME));

        // now pass in consumer type with null id just like the client would
        ConsumerType type = new ConsumerType(standardSystemType.getLabel());
        assertNull(type.getId());
        Consumer nulltypeid = new Consumer(CONSUMER_NAME, USER_NAME, null, type);
        submitted = consumerResource.create(
            nulltypeid,
            TestUtil.createPrincipal(someuser.getUsername(), owner, Access.ALL),
            null, null, null, true);
        assertNotNull(submitted);
        assertEquals(nulltypeid.getUuid(), submitted.getUuid());
        assertNotNull(submitted.getType().getId());
    }

    @Test
    public void unbindBySerialWithExistingCertificateShouldPass() {
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        List<EntitlementCertificate> serials = consumerResource
            .getEntitlementCertificates(consumer.getUuid(), null);
        assertEquals(1, serials.size());

        consumerResource.unbindBySerial(consumer.getUuid(), serials.get(0)
            .getSerial().getId());
        assertEquals(0, consumerResource.listEntitlements(
            consumer.getUuid(), null, true, "", new ArrayList<KeyValueParameter>(), null).size());
    }

    @Test(expected = NotFoundException.class)
    public void testCannotGetAnotherConsumersCerts() {
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);

        Consumer evilConsumer = TestUtil.createConsumer(standardSystemType,
            owner);
        consumerCurator.create(evilConsumer);
        setupPrincipal(new ConsumerPrincipal(evilConsumer));

        securityInterceptor.enable();

        consumerResource.getEntitlementCertificates(consumer.getUuid(), null);
    }

    @Test
    public void testCanGetOwnedConsumersCerts() {
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);

        setupPrincipal(new ConsumerPrincipal(consumer));

        assertEquals(3, consumerResource.getEntitlementCertificates(
            consumer.getUuid(), null).size());
    }

    @Test(expected = NotFoundException.class)
    public void canNotDeleteConsumerOtherThanSelf() {
        Consumer evilConsumer = TestUtil.createConsumer(standardSystemType,
            owner);
        consumerCurator.create(evilConsumer);
        setupPrincipal(new ConsumerPrincipal(evilConsumer));

        securityInterceptor.enable();

        consumerResource.deleteConsumer(consumer.getUuid(), principal);
    }

    @Test
    public void consumerCanDeleteSelf() throws GeneralSecurityException,
        IOException {
        Consumer toSubmit = new Consumer(CONSUMER_NAME, USER_NAME, owner,
            standardSystemType);

        toSubmit.getFacts().put(METADATA_NAME, METADATA_VALUE);
        Consumer c = consumerCurator.create(toSubmit);

        IdentityCertificate idCert = icsa.generateIdentityCert(c);
        c.setIdCert(idCert);
        setupPrincipal(new ConsumerPrincipal(c));
        consumerResource.deleteConsumer(c.getUuid(), principal);
    }

    @Test
    public void getConsumersCerts() {
        setupAdminPrincipal("admin");
        securityInterceptor.enable();

        assertEquals(0, consumerResource.getEntitlementCertificates(
            consumer.getUuid(), null).size());
    }

    @Test(expected = NotFoundException.class)
    public void testCannotGetAnotherOwnersConsumersCerts() {
        Consumer evilConsumer = TestUtil.createConsumer(standardSystemType,
            owner);
        consumerCurator.create(evilConsumer);

        Owner evilOwner = ownerCurator.create(new Owner("another-owner"));
        ownerCurator.create(evilOwner);

        setupPrincipal(evilOwner, Access.ALL);
        securityInterceptor.enable();

        consumerResource.getEntitlementCertificates(consumer.getUuid(), null);
    }

    @Test(expected = ForbiddenException.class)
    public void testConsumerCannotListAllConsumers() {
        setupPrincipal(new ConsumerPrincipal(consumer));
        securityInterceptor.enable();

        consumerResource.list(null, null, null, new ArrayList<String>(), null, null, null);
    }

    @Test(expected = BadRequestException.class)
    public void testConsumerCannotListWithUuidsAndOtherParameters() {
        Consumer consumer = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(consumer);

        setupAdminPrincipal("admin");
        securityInterceptor.enable();
        List<String> uuidList = new ArrayList<String>();
        uuidList.add(consumer.getUuid());
        consumerResource.list("username", toSet("typeLabel"), owner.getKey(), uuidList,
            null, null, new PageRequest());
    }

    @Test
    public void consumerShouldSeeOwnEntitlements() {
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);

        setupPrincipal(new ConsumerPrincipal(consumer));
        securityInterceptor.enable();

        assertEquals(3, consumerResource.listEntitlements(
            consumer.getUuid(), null, true, "", new ArrayList<KeyValueParameter>(), null).size());
    }

    @Test(expected = NotFoundException.class)
    public void consumerShouldNotSeeAnotherConsumersEntitlements() {
        Consumer evilConsumer = TestUtil.createConsumer(standardSystemType,
            owner);
        consumerCurator.create(evilConsumer);

        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        consumerResource.bind(evilConsumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);

        setupPrincipal(new ConsumerPrincipal(evilConsumer));
        securityInterceptor.enable();

        consumerResource.listEntitlements(consumer.getUuid(), null, true,
            "", new ArrayList<KeyValueParameter>(), null);
    }

    @Test(expected = NotFoundException.class)
    public void ownerShouldNotSeeOtherOwnerEntitlements() {
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);

        Owner evilOwner = ownerCurator.create(new Owner("another-owner"));
        ownerCurator.create(evilOwner);

        securityInterceptor.enable();
        setupPrincipal(evilOwner, Access.ALL);

        consumerResource.listEntitlements(consumer.getUuid(), null, true,
            "", new ArrayList<KeyValueParameter>(), null);
    }

    @Test
    public void ownerShouldSeeOwnEntitlements() {
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null, null, null, null);

        securityInterceptor.enable();

        assertEquals(3, consumerResource.listEntitlements(
            consumer.getUuid(), null, true, "", new ArrayList<KeyValueParameter>(), null).size());
    }

    @Test
    public void personalNameOverride() {
        Consumer personal = TestUtil.createConsumer(personType, owner);

        personal = consumerResource.create(personal, principal, null, null, null, true);

        // Not sure if this should be hard-coded to default
        assertEquals(USER_NAME, personal.getName());
    }

    @Test
    public void userwithEmail() {
        String username = "(foo)@{baz}.[com]&?";
        User u = userCurator.create(new User(username, "dontcare"));
        ownerAdminRole.addUser(u);
        roleCurator.merge(ownerAdminRole);

        Principal emailuser = TestUtil.createPrincipal(username, owner,
            Access.ALL);
        setupPrincipal(emailuser);

        Consumer personal = TestUtil.createConsumer(personType, owner);
        personal.setName(((UserPrincipal) emailuser).getUsername());

        personal = consumerResource.create(personal, emailuser, username, null, null, true);

        // Not sure if this should be hard-coded to default
        assertEquals(username, personal.getName());
    }

    @Test(expected = BadRequestException.class)
    public void onlyOnePersonalConsumer() {
        Consumer personal = TestUtil.createConsumer(personType, owner);
        consumerResource.create(personal, principal, null, null, null, true);

        personal = TestUtil.createConsumer(personType, owner);
        consumerResource.create(personal, principal, null, null, null, true);
    }

    /**
     * Test verifies that list of certs changes after regeneration
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testRegenerateEntitlementCertificateWithValidConsumerByEntitlement() {
        ConsumerResource cr = new ConsumerResource(this.consumerCurator, null,
            null, null, this.entitlementCurator, null, null, null, null, null,
            null, null, null, this.poolManager, null, null, null,
            null, null, null, null, null, new CandlepinCommonTestConfig(), null,
            null, null, mock(ConsumerBindUtil.class), null);

        Response rsp = consumerResource.bind(
            consumer.getUuid(), pool.getId().toString(), null, 1, null,
            null, false, null, null, null, null);

        List<Entitlement> resultList = (List<Entitlement>) rsp.getEntity();
        Entitlement ent = resultList.get(0);
        Set<EntitlementCertificate> entCertsBefore = ent.getCertificates();

        cr.regenerateEntitlementCertificates(this.consumer.getUuid(),
            ent.getId(), true);
        Set<EntitlementCertificate> entCertsAfter = ent.getCertificates();

        assertFalse(entCertsBefore.equals(entCertsAfter));
    }

    @Test(expected = BadRequestException.class)
    public void testInvalidProductId() {
        consumerResource.bind(consumer.getUuid(), "JarjarBinks", null,
            null, null, null, false, null, null, null, null);
    }

    private static class ProductCertCreationModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(Configuration.class).to(CandlepinCommonTestConfig.class);
            bind(PKIReader.class).to(BouncyCastlePKIReader.class)
                .asEagerSingleton();
        }
    }

    private Set<String> toSet(String s) {
        Set<String> result = new HashSet<String>();
        result.add(s);
        return result;
    }
}
