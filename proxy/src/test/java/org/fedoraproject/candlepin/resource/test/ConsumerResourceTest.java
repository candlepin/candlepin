/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.resource.test;

import static org.fedoraproject.candlepin.test.TestUtil.createIdCert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;

import org.fedoraproject.candlepin.audit.EventFactory;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.auth.Access;
import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.auth.NoAuthPrincipal;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.auth.permissions.Permission;
import org.fedoraproject.candlepin.controller.CandlepinPoolManager;
import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.ActivationKey;
import org.fedoraproject.candlepin.model.ActivationKeyCurator;
import org.fedoraproject.candlepin.model.CertificateSerialDto;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.ConsumerInstalledProduct;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.IdentityCertificate;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.OwnerPermission;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Role;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.fedoraproject.candlepin.pki.PKIReader;
import org.fedoraproject.candlepin.pki.impl.BouncyCastlePKIReader;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.resource.util.ResourceDateParser;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.IdentityCertServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestDateUtil;
import org.fedoraproject.candlepin.test.TestUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

/**
 * ConsumerResourceTest
 */
@RunWith(MockitoJUnitRunner.class)
public class ConsumerResourceTest extends DatabaseTestFixture {

    private static final String METADATA_VALUE = "jsontestname";
    private static final String METADATA_NAME = "name";
    private static final String CONSUMER_NAME = "consumer_name";
    private static final String USER_NAME = "testing user";
    private static final String NON_EXISTENT_CONSUMER = "i don't exist";

    @Mock
    private ConsumerCurator mockedConsumerCurator;
    @Mock
    private EntitlementCertServiceAdapter mockedEntitlementCertServiceAdapter;

    private ConsumerType standardSystemType;
    private ConsumerType personType;
    private Consumer consumer;
    private Product product;
    private Pool pool;

    private ConsumerResource consumerResource;
    private Principal principal;
    private Owner owner;
    private Role ownerAdminRole;

    private User someuser;

    @Override
    protected Module getGuiceOverrideModule() {
        return new ProductCertCreationModule();
    }

    @Before
    public void setUp() {
        consumerResource = injector.getInstance(ConsumerResource.class);

        standardSystemType = consumerTypeCurator.create(new ConsumerType(
            "standard-system"));

        personType = consumerTypeCurator.create(new ConsumerType(
            ConsumerTypeEnum.PERSON));
        owner = ownerCurator.create(new Owner("test-owner"));
        ownerCurator.create(owner);

        someuser = userCurator.create(new User(USER_NAME, "dontcare"));

        ownerAdminRole = createAdminRole(owner);
        ownerAdminRole.addUser(someuser);
        roleCurator.create(ownerAdminRole);

        principal = new UserPrincipal(USER_NAME,
                new ArrayList<Permission>(ownerAdminRole.getPermissions()), false);
        setupPrincipal(principal);

        consumer = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(consumer);

        product = TestUtil.createProduct();
        productCurator.create(product);

        pool = createPoolAndSub(owner, product, 10L,
            TestDateUtil.date(2010, 1, 1), TestDateUtil.date(2020, 12, 31));
        poolCurator.create(pool);
    }

    @Test
    public void testGetCerts() {

        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
        List<EntitlementCertificate> serials = consumerResource
            .getEntitlementCertificates(consumer.getUuid(), null);
        assertEquals(1, serials.size());
    }

    @Test
    public void testGetCertSerials() {
        Consumer consumer = createConsumer();
        List<EntitlementCertificate> certificates = createEntitlementCertificates();

        when(mockedEntitlementCertServiceAdapter.listForConsumer(consumer))
            .thenReturn(certificates);
        when(mockedConsumerCurator.findByUuid(consumer.getUuid())).thenReturn(
            consumer);

        ConsumerResource consumerResource = new ConsumerResource(
            mockedConsumerCurator, null, null, null, null, null,
            mockedEntitlementCertServiceAdapter, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null);

        List<CertificateSerialDto> serials = consumerResource
            .getEntitlementCertificateSerials(consumer.getUuid());

        verifyCertificateSerialNumbers(serials);
    }

    private void verifyCertificateSerialNumbers(
        List<CertificateSerialDto> serials) {
        assertEquals(3, serials.size());
        assertTrue(serials.get(0).getSerial() > 0);

    }

    private List<EntitlementCertificate> createEntitlementCertificates() {
        return Arrays.asList(new EntitlementCertificate[]{
            createEntitlementCertificate("key1", "cert1"),
            createEntitlementCertificate("key2", "cert2"),
            createEntitlementCertificate("key3", "cert3") });
    }

    private Consumer createConsumer() {
        return new Consumer("test-consumer", "test-user", new Owner(
            "Test Owner"), new ConsumerType("test-consumer-type-"));
    }

    @Test
    public void testGetSerialFiltering() {
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
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
    public void testCreateConsumer() {
        Consumer toSubmit = new Consumer(CONSUMER_NAME, USER_NAME, null,
            standardSystemType);
        toSubmit.getFacts().put(METADATA_NAME, METADATA_VALUE);
        Consumer submitted = consumerResource.create(
            toSubmit,
            new UserPrincipal(someuser.getUsername(), Arrays.asList(new Permission [] {
                new OwnerPermission(owner, Access.ALL) }), false),
            someuser.getUsername(),
            owner.getKey(), null);

        assertNotNull(submitted);
        assertNotNull(consumerCurator.find(submitted.getId()));
        assertEquals(standardSystemType.getLabel(), submitted.getType()
            .getLabel());
        assertEquals(METADATA_VALUE, submitted.getFact(METADATA_NAME));
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
                owner.getKey(), null);
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
        consumerResource.create(anotherToSubmit, principal, null, owner.getKey(), null);
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
            null);

        assertEquals(USER_NAME, consumer.getUsername());
    }

    @Test(expected = ForbiddenException.class)
    public void testReadOnlyUsersCantGenerateExports() {
        consumer.setType(consumerTypeCurator.create(
            new ConsumerType(ConsumerTypeEnum.CANDLEPIN)));
        consumerCurator.update(consumer);
        setupPrincipal(owner, Access.READ_ONLY);
        securityInterceptor.enable();
        consumerResource.exportData(mock(HttpServletResponse.class), consumer.getUuid());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBindByPool() throws Exception {

        Response rsp = consumerResource.bind(
            consumer.getUuid(), pool.getId().toString(), null, 1, null,
            null, false, null);

        List<Entitlement> resultList = (List<Entitlement>) rsp.getEntity();

        consumer = consumerCurator.findByUuid(consumer.getUuid());
        assertEquals(1, consumer.getEntitlements().size());

        pool = poolCurator.find(pool.getId());
        assertEquals(Long.valueOf(1), pool.getConsumed());
        assertEquals(1, resultList.size());
        assertEquals(pool.getId(), resultList.get(0).getPool().getId());
        assertEquals(1, entCertCurator.listForEntitlement(resultList.get(0))
            .size());
    }

    @Test(expected = BadRequestException.class)
    public void testBindMultipleParams() throws Exception {
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            new String[]{"12232"}, 1, null, null, false, null);
    }

    @Test(expected = NotFoundException.class)
    public void testBindByPoolBadConsumerUuid() throws Exception {
        consumerResource.bind("notarealuuid", pool.getId(), null, null, null,
            null, false, null);
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
            null, null, null);

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
            null, null, null);
        assertNotNull(submitted);
        assertEquals(nulltypeid.getUuid(), submitted.getUuid());
        assertNotNull(submitted.getType().getId());
    }

    @Test
    public void unbindBySerialWithExistingCertificateShouldPass() {
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
        List<EntitlementCertificate> serials = consumerResource
            .getEntitlementCertificates(consumer.getUuid(), null);
        assertEquals(1, serials.size());

        consumerResource.unbindBySerial(consumer.getUuid(), serials.get(0)
            .getSerial().getId());
        assertEquals(0,
            consumerResource.listEntitlements(consumer.getUuid(), null).size());
    }

    @Test(expected = NotFoundException.class)
    public void unbindByInvalidSerialShouldFail() {
        consumerResource
            .unbindBySerial(consumer.getUuid(), Long.valueOf(1234L));
    }

    @Test(expected = NotFoundException.class)
    public void unbindBySerialWithInvalidUuidShouldFail() {
        consumerResource.unbindBySerial(NON_EXISTENT_CONSUMER,
            Long.valueOf(1234L));
    }

    @Test(expected = ForbiddenException.class)
    public void testCannotGetAnotherConsumersCerts() {
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);

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
            null, 1, null, null, false, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);

        setupPrincipal(new ConsumerPrincipal(consumer));

        assertEquals(
            3,
            consumerResource.getEntitlementCertificates(consumer.getUuid(),
                null).size());
    }

    @Test(expected = ForbiddenException.class)
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

        IdentityCertServiceAdapter icsa = injector
            .getInstance(IdentityCertServiceAdapter.class);
        IdentityCertificate idCert = icsa.generateIdentityCert(c);
        c.setIdCert(idCert);
        setupPrincipal(new ConsumerPrincipal(c));
        consumerResource.deleteConsumer(c.getUuid(), principal);
    }

    @Test
    public void getConsumersCerts() {
        setupAdminPrincipal("admin");
        securityInterceptor.enable();

        assertEquals(0,
            consumerResource.getEntitlementCertificates(consumer.getUuid(),
                null).size());
    }

    @Test(expected = ForbiddenException.class)
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

        consumerResource.list(null, null, null);
    }

    @Test
    public void consumerShouldSeeOwnEntitlements() {
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);

        setupPrincipal(new ConsumerPrincipal(consumer));
        securityInterceptor.enable();

        assertEquals(3,
            consumerResource.listEntitlements(consumer.getUuid(), null).size());
    }

    @Test(expected = ForbiddenException.class)
    public void consumerShouldNotSeeAnotherConsumersEntitlements() {
        Consumer evilConsumer = TestUtil.createConsumer(standardSystemType,
            owner);
        consumerCurator.create(evilConsumer);

        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
        consumerResource.bind(evilConsumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);

        setupPrincipal(new ConsumerPrincipal(evilConsumer));
        securityInterceptor.enable();

        consumerResource.listEntitlements(consumer.getUuid(), null);
    }

    @Test(expected = ForbiddenException.class)
    public void ownerShouldNotSeeOtherOwnerEntitlements() {
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);

        Owner evilOwner = ownerCurator.create(new Owner("another-owner"));
        ownerCurator.create(evilOwner);

        securityInterceptor.enable();
        setupPrincipal(evilOwner, Access.ALL);

        consumerResource.listEntitlements(consumer.getUuid(), null);
    }

    @Test
    public void ownerShouldSeeOwnEntitlements() {
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);
        consumerResource.bind(consumer.getUuid(), pool.getId().toString(),
            null, 1, null, null, false, null);

        securityInterceptor.enable();

        assertEquals(3,
            consumerResource.listEntitlements(consumer.getUuid(), null).size());
    }

    @Test
    public void personalNameOverride() {
        Consumer personal = TestUtil.createConsumer(personType, owner);

        personal = consumerResource.create(personal, principal, null, null, null);

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

        Consumer personal = TestUtil.createConsumer(personType, owner);
        personal.setName(((UserPrincipal) emailuser).getUsername());

        personal = consumerResource.create(personal, emailuser, username, null, null);

        // Not sure if this should be hard-coded to default
        assertEquals(username, personal.getName());
    }

    @Test(expected = BadRequestException.class)
    public void onlyOnePersonalConsumer() {
        Consumer personal = TestUtil.createConsumer(personType, owner);
        consumerResource.create(personal, principal, null, null, null);

        personal = TestUtil.createConsumer(personType, owner);
        consumerResource.create(personal, principal, null, null, null);
    }

    /**
     * Basic test. If invalid id is given, should throw
     * {@link NotFoundException}
     */
    @Test(expected = NotFoundException.class)
    public void testRegenerateEntitlementCertificatesWithInvalidConsumerId() {
        this.consumerResource.regenerateEntitlementCertificates("xyz", null);
    }

    /**
     * Test just verifies that entitler is called only once and it doesn't need
     * any other object to execute.
     */
    @Test
    public void testRegenerateEntitlementCertificateWithValidConsumer() {
        CandlepinPoolManager mgr = mock(CandlepinPoolManager.class);
        ConsumerResource cr = new ConsumerResource(this.consumerCurator, null,
            null, null, null, null, null, null, null, null, null, null, null,
            null, mgr, null, null, null, null, null, null);
        cr.regenerateEntitlementCertificates(this.consumer.getUuid(), null);
        Mockito.verify(mgr, Mockito.times(1))
            .regenerateEntitlementCertificates(eq(this.consumer));
    }

    /**
     * Test verifies that list of certs changes after regeneration
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testRegenerateEntitlementCertificateWithValidConsumerByEntitlement() {
        ConsumerResource cr = new ConsumerResource(this.consumerCurator, null,
            null, null, this.entitlementCurator, null, null, null, null, null,
            null, null, null, null, this.poolManager, null, null, null,
            null, null, null);

        Response rsp = consumerResource.bind(
            consumer.getUuid(), pool.getId().toString(), null, 1, null,
            null, false, null);

        List<Entitlement> resultList = (List<Entitlement>) rsp.getEntity();
        Entitlement ent = resultList.get(0);
        Set<EntitlementCertificate> entCertsBefore = ent.getCertificates();

        cr.regenerateEntitlementCertificates(this.consumer.getUuid(),
            ent.getId());
        Set<EntitlementCertificate> entCertsAfter = ent.getCertificates();

        assertFalse(entCertsBefore.equals(entCertsAfter));
    }

    @Test
    public void testInvalidProductId() {
        try {
            consumerResource.bind(consumer.getUuid(), "JarjarBinks", null,
                null, null, null, false, null);
        }
        catch (BadRequestException e) {
            // this is expected
            return;
        }
        fail("No Exception was thrown");
    }

    @Test
    public void testRegenerateIdCerts() throws GeneralSecurityException,
        IOException {

        // using lconsumer simply to avoid hiding consumer. This should
        // get renamed once we refactor this test suite.
        IdentityCertServiceAdapter mockedIdSvc = Mockito
            .mock(IdentityCertServiceAdapter.class);

        EventSink sink = Mockito.mock(EventSink.class);
        EventFactory factory = Mockito.mock(EventFactory.class);

        Consumer lconsumer = createConsumer();
        lconsumer.setIdCert(createIdCert());
        IdentityCertificate ic = lconsumer.getIdCert();
        assertNotNull(ic);

        when(mockedConsumerCurator.findByUuid(lconsumer.getUuid())).thenReturn(
            lconsumer);
        when(mockedIdSvc.regenerateIdentityCert(lconsumer)).thenReturn(
            createIdCert());

        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null,
            null, null, null, mockedIdSvc, null, null, sink, factory, null,
            null, null, null, null, null, null, ownerCurator, null, null, null);

        Consumer fooc = cr.regenerateIdentityCertificates(lconsumer.getUuid());

        assertNotNull(fooc);
        IdentityCertificate ic1 = fooc.getIdCert();
        assertNotNull(ic1);
        assertFalse(ic.equals(ic1));
    }

    private static class ProductCertCreationModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(PKIReader.class).to(BouncyCastlePKIReader.class)
                .asEagerSingleton();
        }
    }

    @Test(expected = BadRequestException.class)
    public void testCreatePersonConsumerWithActivationKey() {
        Consumer c = mock(Consumer.class);
        Owner o = mock(Owner.class);
        ActivationKey ak = mock(ActivationKey.class);
        NoAuthPrincipal nap = mock(NoAuthPrincipal.class);
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        OwnerCurator oc = mock(OwnerCurator.class);
        ConsumerTypeCurator ctc = mock(ConsumerTypeCurator.class);

        ConsumerType cType = new ConsumerType(ConsumerTypeEnum.PERSON);
        when(ak.getId()).thenReturn("testKey");
        when(o.getKey()).thenReturn("testOwner");
        when(akc.lookupForOwner(eq("testKey"), eq(o))).thenReturn(ak);
        when(oc.lookupByKey(eq("testOwner"))).thenReturn(o);
        when(c.getType()).thenReturn(cType);
        when(c.getName()).thenReturn("testConsumer");
        when(ctc.lookupByLabel(eq("person"))).thenReturn(cType);

        ConsumerResource cr = new ConsumerResource(null, ctc,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, oc, akc, null, null);
        cr.create(c, nap, null, "testOwner", "testKey");
    }

    @Test
    public void testProductNoPool() {
        try {
            Consumer c = mock(Consumer.class);
            Owner o = mock(Owner.class);
            SubscriptionServiceAdapter sa = mock(SubscriptionServiceAdapter.class);
            Entitler e = mock(Entitler.class);
            ConsumerCurator cc = mock(ConsumerCurator.class);
            String[] prodIds = {"notthere"};

            when(c.getOwner()).thenReturn(o);
            when(sa.hasUnacceptedSubscriptionTerms(eq(o))).thenReturn(false);
            when(cc.findByUuid(eq("fakeConsumer"))).thenReturn(c);
            when(e.bindByProducts(eq(prodIds), eq(c), eq((Date) null)))
                .thenThrow(new RuntimeException());

            ConsumerResource cr = new ConsumerResource(cc, null,
                null, sa, null, null, null, i18n, null, null, null,
                null, null, null, null, null, null, null, null, e, null);
            cr.bind("fakeConsumer", null, prodIds, 1, null, null, false, null);
        }
        catch (Throwable t) {
            fail("Runtime exception should be caught in ConsumerResource.bind");
        }
    }

    @Test
    public void futureHealing() {
        Consumer c = mock(Consumer.class);
        Owner o = mock(Owner.class);
        SubscriptionServiceAdapter sa = mock(SubscriptionServiceAdapter.class);
        Entitler e = mock(Entitler.class);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        ConsumerInstalledProduct cip = mock(ConsumerInstalledProduct.class);
        Set<ConsumerInstalledProduct> products = new HashSet<ConsumerInstalledProduct>();
        products.add(cip);

        when(c.getOwner()).thenReturn(o);
        when(cip.getProductId()).thenReturn("product-foo");
        when(sa.hasUnacceptedSubscriptionTerms(eq(o))).thenReturn(false);
        when(cc.findByUuid(eq("fakeConsumer"))).thenReturn(c);

        ConsumerResource cr = new ConsumerResource(cc, null,
            null, sa, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, e, null);
        String dtStr = "2011-09-26T18:10:50.184081+00:00";
        Date dt = ResourceDateParser.parseDateString(dtStr);
        cr.bind("fakeConsumer", null, null, 1, null, null, false, dtStr);
        verify(e).bindByProducts(eq((String []) null), eq(c), eq(dt));
    }
}
