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
package org.candlepin.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import org.candlepin.async.JobManager;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.ConsumerManager;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.OwnerManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.api.server.v1.CryptographicCapabilitiesDTO;
import org.candlepin.dto.api.server.v1.NestedOwnerDTO;
import org.candlepin.dto.api.server.v1.UeberCertificateDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ConflictException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Owner;
import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.candlepin.paging.PagingUtilFactory;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.OidUtil;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.certs.UeberCertificateGenerator;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.test.CryptoUtil;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.ServiceLevelValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.KeyException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;



public class OwnerResourceUeberCertificateTest extends DatabaseTestFixture {

    public static Stream<Arguments> schemeSource() {
        return CryptoUtil.SUPPORTED_SCHEMES.values()
            .stream()
            .map(Arguments::of);
    }

    public static Stream<Arguments> supportedSignatureAlgorithmsSource() {
        return CryptoUtil.SUPPORTED_SCHEMES.values()
            .stream()
            .map(Scheme::signatureAlgorithm)
            .map(Arguments::of);
    }

    public static Stream<Arguments> supportedKeyAlgorithmsSource() {
        return CryptoUtil.SUPPORTED_SCHEMES.values()
            .stream()
            .map(Scheme::keyAlgorithm)
            .map(Arguments::of);
    }

    private UserPrincipal createUserPrincipal(Owner owner, String username) {
        Role ownerAdminRole = this.createAdminRole(owner);
        this.roleCurator.create(ownerAdminRole);

        User user = new User(username, "password");

        List<Permission> permissions = this.permissionFactory
            .createPermissions(user, ownerAdminRole.getPermissions());

        return new UserPrincipal(user.getUsername(), permissions, false);
    }

    private PrincipalProvider createPrincipalProvider(Principal principal) {
        return new PrincipalProvider() {
            @Override
            public Principal get() {
                return principal;
            }
        };
    }

    private OwnerResource buildOwnerResource(PrincipalProvider principalProvider,
        UeberCertificateGenerator ueberCertificateGenerator) {

        return new OwnerResource(
            this.ownerCurator,
            this.activationKeyCurator,
            this.consumerCurator,
            this.injector.getInstance(ConsumerManager.class),
            this.i18n,
            this.injector.getInstance(EventSink.class),
            this.injector.getInstance(EventFactory.class),
            this.injector.getInstance(ContentAccessManager.class),
            this.injector.getInstance(ManifestManager.class),
            this.injector.getInstance(PoolManager.class),
            this.injector.getInstance(PoolService.class),
            this.poolCurator,
            this.injector.getInstance(OwnerManager.class),
            this.exporterMetadataCurator,
            this.ownerInfoCurator,
            this.importRecordCurator,
            this.entitlementCurator,
            this.ueberCertificateCurator,
            ueberCertificateGenerator,
            this.environmentCurator,
            this.injector.getInstance(CalculatedAttributesUtil.class),
            this.injector.getInstance(ContentOverrideValidator.class),
            this.injector.getInstance(ServiceLevelValidator.class),
            this.injector.getInstance(OwnerServiceAdapter.class),
            TestConfig.defaults(),
            this.injector.getInstance(ConsumerTypeValidator.class),
            this.productCurator,
            this.modelTranslator,
            this.injector.getInstance(JobManager.class),
            this.injector.getInstance(DTOValidator.class),
            principalProvider,
            this.injector.getInstance(PagingUtilFactory.class),
            this.injector.getInstance(CryptoManager.class),
            this.injector.getInstance(OidUtil.class));
    }

    private OwnerResource buildOwnerResource(PrincipalProvider principalProvider) {
        return this.buildOwnerResource(principalProvider,
            this.injector.getInstance(UeberCertificateGenerator.class));
    }

    private OwnerResource buildOwnerResource() {
        Principal principal = new UserPrincipal("superadmin", null, true);
        PrincipalProvider principalProvider = this.createPrincipalProvider(principal);

        return this.buildOwnerResource(principalProvider);
    }

    private CryptographicCapabilitiesDTO buildCryptoCapabilitiesFromSchemes(Collection<Scheme> schemes) {
        OidUtil oidUtil = CryptoUtil.getOidUtil();

        List<String> keyAlgorithmOids = schemes.stream()
            .map(Scheme::keyAlgorithm)
            .map(oidUtil::getKeyAlgorithmOid)
            .map(opt -> opt.orElseThrow(() -> new RuntimeException("could not translate algorithm to OID")))
            .toList();

        List<String> sigAlgorithmOids = schemes.stream()
            .map(Scheme::signatureAlgorithm)
            .map(oidUtil::getSignatureAlgorithmOid)
            .map(opt -> opt.orElseThrow(() -> new RuntimeException("could not translate algorithm to OID")))
            .toList();

        return new CryptographicCapabilitiesDTO()
            .keyAlgorithms(keyAlgorithmOids)
            .signatureAlgorithms(sigAlgorithmOids);
    }

    private void assertCertificateObeysCryptoCapabilities(UeberCertificateDTO container,
        CryptographicCapabilitiesDTO capabilities) throws CertificateException, KeyException {

        OidUtil oidUtil = CryptoUtil.getOidUtil();

        PrivateKey pkey = CryptoUtil.extractPrivateKeyFromContainerString(container.getKey());
        String keyAlgorithmOid = oidUtil.getKeyAlgorithmOid(pkey.getAlgorithm())
            .orElseThrow(() -> new RuntimeException("could not translate algorithm to OID"));

        X509Certificate x509cert = CryptoUtil.extractCertificateFromContainerString(container.getCert());
        String sigAlgorithmOid = x509cert.getSigAlgOID();

        if (capabilities.getKeyAlgorithms() != null && !capabilities.getKeyAlgorithms().isEmpty()) {
            assertThat(keyAlgorithmOid).isIn(capabilities.getKeyAlgorithms());
        }

        if (capabilities.getSignatureAlgorithms() != null &&
            !capabilities.getSignatureAlgorithms().isEmpty()) {

            assertThat(sigAlgorithmOid).isIn(capabilities.getSignatureAlgorithms());
        }
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testCreateUeberCertificate(Scheme scheme) throws Exception {
        Owner owner = this.createOwner();
        UserPrincipal principal = this.createUserPrincipal(owner, "test_user");
        PrincipalProvider principalProvider = this.createPrincipalProvider(principal);

        CryptographicCapabilitiesDTO capabilities = this.buildCryptoCapabilitiesFromSchemes(
            List.of(scheme));

        OwnerResource resource = this.buildOwnerResource(principalProvider);

        UeberCertificateDTO output = resource.createUeberCertificate(owner.getKey(), capabilities);
        assertThat(output)
            .isNotNull()
            .doesNotReturn(null, UeberCertificateDTO::getKey)
            .doesNotReturn(null, UeberCertificateDTO::getCert)
            .extracting(UeberCertificateDTO::getOwner)
            .returns(owner.getKey(), NestedOwnerDTO::getKey);

        // TODO: Verify the principal exists in the cert somewhere

        // Verify the cert has the correct validity range. It needs to be active at the time of generation,
        // and up to December, 2049
        X509Certificate x509cert = CryptoUtil.extractCertificateFromContainerString(output.getCert());
        assertThat(x509cert.getNotBefore())
            .isNotNull()
            .isBeforeOrEqualTo(new Date());

        assertThat(x509cert.getNotAfter())
            .isNotNull()
            .isAfter(Date.from(OffsetDateTime.of(2049, 12, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()));

        // Verify the cert was issued by the scheme's CA cert, and isn't self-signed
        assertThat(x509cert.getIssuerX500Principal())
            .isNotEqualTo(x509cert.getSubjectX500Principal())
            .isEqualTo(scheme.certificate().getSubjectX500Principal());

        this.assertCertificateObeysCryptoCapabilities(output, capabilities);
    }

    @Test
    public void testCreateUeberCertificateWithoutCryptoCapabilities() throws Exception {
        Owner owner = this.createOwner();
        OwnerResource resource = this.buildOwnerResource();

        UeberCertificateDTO output = resource.createUeberCertificate(owner.getKey(), null);
        assertThat(output)
            .isNotNull()
            .doesNotReturn(null, UeberCertificateDTO::getKey)
            .doesNotReturn(null, UeberCertificateDTO::getCert)
            .extracting(UeberCertificateDTO::getOwner)
            .returns(owner.getKey(), NestedOwnerDTO::getKey);

        // Verify the crypto assets are real by way of extraction
        X509Certificate x509cert = CryptoUtil.extractCertificateFromContainerString(output.getCert());
        PrivateKey pkey = CryptoUtil.extractPrivateKeyFromContainerString(output.getKey());
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testCreateUeberCertificateTreatsEmptyCapabilitiesAsAbsent(List<String> emptyAlgorithms)
        throws Exception {

        // This test verifies that createUeberCertificate treats both empty and null lists within a present
        // capabilities DTO as if they were absent.

        Owner owner = this.createOwner();
        OwnerResource resource = this.buildOwnerResource();

        CryptographicCapabilitiesDTO capabilities = new CryptographicCapabilitiesDTO()
            .keyAlgorithms(emptyAlgorithms)
            .signatureAlgorithms(emptyAlgorithms);

        UeberCertificateDTO output = resource.createUeberCertificate(owner.getKey(), capabilities);
        assertThat(output)
            .isNotNull()
            .doesNotReturn(null, UeberCertificateDTO::getKey)
            .doesNotReturn(null, UeberCertificateDTO::getCert)
            .extracting(UeberCertificateDTO::getOwner)
            .returns(owner.getKey(), NestedOwnerDTO::getKey);

        // Verify the crypto assets are real by way of extraction
        X509Certificate x509cert = CryptoUtil.extractCertificateFromContainerString(output.getCert());
        PrivateKey pkey = CryptoUtil.extractPrivateKeyFromContainerString(output.getKey());
    }

    @Test
    public void testCreateUeberCertificateWithFullCryptoCapabilities() throws Exception {
        Owner owner = this.createOwner();
        OwnerResource resource = this.buildOwnerResource();

        CryptographicCapabilitiesDTO capabilities = this.buildCryptoCapabilitiesFromSchemes(
            CryptoUtil.SUPPORTED_SCHEMES.values());

        UeberCertificateDTO output = resource.createUeberCertificate(owner.getKey(), capabilities);
        assertThat(output)
            .isNotNull()
            .doesNotReturn(null, UeberCertificateDTO::getKey)
            .doesNotReturn(null, UeberCertificateDTO::getCert)
            .extracting(UeberCertificateDTO::getOwner)
            .returns(owner.getKey(), NestedOwnerDTO::getKey);

        this.assertCertificateObeysCryptoCapabilities(output, capabilities);
    }

    private void ueberCertificateCapabilitiesTest(List<String> keyAlgorithms, List<String> sigAlgorithms)
        throws Exception {

        Owner owner = this.createOwner();
        OwnerResource resource = this.buildOwnerResource();

        CryptographicCapabilitiesDTO capabilities = new CryptographicCapabilitiesDTO()
            .keyAlgorithms(keyAlgorithms)
            .signatureAlgorithms(sigAlgorithms);

        UeberCertificateDTO output = resource.createUeberCertificate(owner.getKey(), capabilities);
        assertThat(output)
            .isNotNull()
            .doesNotReturn(null, UeberCertificateDTO::getKey)
            .doesNotReturn(null, UeberCertificateDTO::getCert)
            .extracting(UeberCertificateDTO::getOwner)
            .returns(owner.getKey(), NestedOwnerDTO::getKey);

        this.assertCertificateObeysCryptoCapabilities(output, capabilities);
    }

    @ParameterizedTest
    @MethodSource("supportedKeyAlgorithmsSource")
    public void testCreateUeberCertificateWithPartialCryptoCapabilitiesHavingNullSignatureAlgorithms(
        String algorithm) throws Exception {

        String algorithmOid = CryptoUtil.getOidUtil().getKeyAlgorithmOid(algorithm)
            .orElseThrow(() -> new RuntimeException("could not translate algorithm to OID"));

        this.ueberCertificateCapabilitiesTest(List.of(algorithmOid), null);
    }

    @ParameterizedTest
    @MethodSource("supportedKeyAlgorithmsSource")
    public void testCreateUeberCertificateWithPartialCryptoCapabilitiesHavingEmptySignatureAlgorithms(
        String algorithm) throws Exception {

        String algorithmOid = CryptoUtil.getOidUtil().getKeyAlgorithmOid(algorithm)
            .orElseThrow(() -> new RuntimeException("could not translate algorithm to OID"));

        this.ueberCertificateCapabilitiesTest(List.of(algorithmOid), List.of());
    }

    @ParameterizedTest
    @MethodSource("supportedSignatureAlgorithmsSource")
    public void testCreateUeberCertificateWithPartialCryptoCapabilitiesHavingNullKeyAlgorithms(
        String algorithm) throws Exception {

        String algorithmOid = CryptoUtil.getOidUtil().getSignatureAlgorithmOid(algorithm)
            .orElseThrow(() -> new RuntimeException("could not translate algorithm to OID"));

        this.ueberCertificateCapabilitiesTest(null, List.of(algorithmOid));
    }

    @ParameterizedTest
    @MethodSource("supportedSignatureAlgorithmsSource")
    public void testCreateUeberCertificateWithPartialCryptoCapabilitiesHavingEmptyKeyAlgorithms(
        String algorithm) throws Exception {

        String algorithmOid = CryptoUtil.getOidUtil().getSignatureAlgorithmOid(algorithm)
            .orElseThrow(() -> new RuntimeException("could not translate algorithm to OID"));

        this.ueberCertificateCapabilitiesTest(List.of(), List.of(algorithmOid));
    }

    @Test
    public void testCreateUeberCertificateWithUnmappableCryptoCapabilitiesPreventsGeneration() {
        Owner owner = this.createOwner();
        OwnerResource resource = this.buildOwnerResource();

        CryptographicCapabilitiesDTO capabilities = new CryptographicCapabilitiesDTO()
            .keyAlgorithms(List.of("1", "2", "3"))
            .signatureAlgorithms(List.of("4", "5", "6"));

        assertThrows(ConflictException.class,
            () -> resource.createUeberCertificate(owner.getKey(), capabilities));
    }

    @Test
    public void testCreateUeberCertificateRegeneratesCertOnSubsequentInvocations() throws Exception {
        Owner owner = this.createOwner();
        OwnerResource resource = this.buildOwnerResource();

        UeberCertificateDTO output1 = resource.createUeberCertificate(owner.getKey(), null);
        assertNotNull(output1);

        UeberCertificateDTO output2 = resource.createUeberCertificate(owner.getKey(), null);
        assertThat(output1)
            .isNotNull()
            .doesNotReturn(null, UeberCertificateDTO::getSerial);

        assertThat(output1)
            .doesNotReturn(output2.getId(), UeberCertificateDTO::getId)
            .doesNotReturn(output2.getKey(), UeberCertificateDTO::getKey)
            .doesNotReturn(output2.getCert(), UeberCertificateDTO::getCert);

        assertThat(output1.getOwner())
            .returns(owner.getKey(), NestedOwnerDTO::getKey);

        assertThat(output2.getOwner())
            .returns(owner.getKey(), NestedOwnerDTO::getKey);

        // The first cert should be revoked if we check its internal state
        assertThat(this.certSerialCurator.get(output1.getSerial().getId()))
            .isNotNull()
            .returns(true, CertificateSerial::isRevoked);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testCreateUeberCertificateRegeneratesCertOnSubsequentInvocationsWithSameCapabilities(
        Scheme scheme) throws Exception {

        Owner owner = this.createOwner();
        OwnerResource resource = this.buildOwnerResource();

        CryptographicCapabilitiesDTO capabilities = this.buildCryptoCapabilitiesFromSchemes(List.of(scheme));

        UeberCertificateDTO output1 = resource.createUeberCertificate(owner.getKey(), capabilities);
        assertThat(output1)
            .isNotNull()
            .doesNotReturn(null, UeberCertificateDTO::getSerial);

        UeberCertificateDTO output2 = resource.createUeberCertificate(owner.getKey(), capabilities);
        assertNotNull(output2);

        assertThat(output1)
            .doesNotReturn(output2.getId(), UeberCertificateDTO::getId)
            .doesNotReturn(output2.getKey(), UeberCertificateDTO::getKey)
            .doesNotReturn(output2.getCert(), UeberCertificateDTO::getCert);

        assertThat(output1.getOwner())
            .returns(owner.getKey(), NestedOwnerDTO::getKey);

        assertThat(output2.getOwner())
            .returns(owner.getKey(), NestedOwnerDTO::getKey);

        assertThat(this.certSerialCurator.get(output1.getSerial().getId()))
            .isNotNull()
            .returns(true, CertificateSerial::isRevoked);
    }

    @Test
    public void testCreateUeberCertificateRegeneratesCertOnSubsequentInvocationsWithDifferentCapabilities()
        throws Exception {

        Owner owner = this.createOwner();
        OwnerResource resource = this.buildOwnerResource();

        // Impl note: if we ever have fewer than two supported schemes, this test will need a severe overhaul
        // or removal.
        Iterator<Scheme> iterator = CryptoUtil.SUPPORTED_SCHEMES.values().iterator();
        Scheme scheme1 = iterator.next();
        Scheme scheme2 = iterator.next();

        CryptographicCapabilitiesDTO caps1 = this.buildCryptoCapabilitiesFromSchemes(List.of(scheme1));
        CryptographicCapabilitiesDTO caps2 = this.buildCryptoCapabilitiesFromSchemes(List.of(scheme2));

        UeberCertificateDTO output1 = resource.createUeberCertificate(owner.getKey(), caps1);
        assertThat(output1)
            .isNotNull()
            .doesNotReturn(null, UeberCertificateDTO::getSerial);

        UeberCertificateDTO output2 = resource.createUeberCertificate(owner.getKey(), caps2);
        assertNotNull(output2);

        assertThat(output1)
            .doesNotReturn(output2.getId(), UeberCertificateDTO::getId)
            .doesNotReturn(output2.getKey(), UeberCertificateDTO::getKey)
            .doesNotReturn(output2.getCert(), UeberCertificateDTO::getCert);

        assertThat(output1.getOwner())
            .returns(owner.getKey(), NestedOwnerDTO::getKey);

        assertThat(output2.getOwner())
            .returns(owner.getKey(), NestedOwnerDTO::getKey);

        assertThat(this.certSerialCurator.get(output1.getSerial().getId()))
            .isNotNull()
            .returns(true, CertificateSerial::isRevoked);
    }

    @Test
    public void testCreateUeberCertificateThrowsApiExceptionOnGenerationFailure() throws Exception {
        Owner owner = this.createOwner();
        UserPrincipal principal = this.createUserPrincipal(owner, "test_user");
        PrincipalProvider principalProvider = this.createPrincipalProvider(principal);
        UeberCertificateGenerator generator = spy(this.injector.getInstance(UeberCertificateGenerator.class));

        // Brittle mock :/
        doThrow(new CertificateException("kaboom"))
            .when(generator)
            .generate(any(Scheme.class), any(Owner.class), any(String.class));

        OwnerResource resource = this.buildOwnerResource(principalProvider, generator);

        assertThrows(BadRequestException.class, () -> resource.createUeberCertificate(owner.getKey(), null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\t" })
    public void testCreateUeberCertificateRequiresValidOwner(String ownerKey) {
        OwnerResource resource = this.buildOwnerResource();

        assertThrows(BadRequestException.class, () -> resource.createUeberCertificate(ownerKey, null));
    }

    @Test
    public void testCreateUeberCertificateReturnsNotFoundWhenOwnerDoesntExist() {
        OwnerResource resource = this.buildOwnerResource();

        assertThrows(NotFoundException.class, () -> resource.createUeberCertificate("404owner", null));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetUeberCertificate(Scheme scheme) throws Exception {
        Owner owner = this.createOwner();
        OwnerResource resource = this.buildOwnerResource();

        CryptographicCapabilitiesDTO capabilities = this.buildCryptoCapabilitiesFromSchemes(List.of(scheme));

        UeberCertificateDTO container = resource.createUeberCertificate(owner.getKey(), capabilities);
        assertThat(container)
            .isNotNull()
            .doesNotReturn(null, UeberCertificateDTO::getKey)
            .doesNotReturn(null, UeberCertificateDTO::getCert)
            .extracting(UeberCertificateDTO::getOwner)
            .returns(owner.getKey(), NestedOwnerDTO::getKey);

        assertCertificateObeysCryptoCapabilities(container, capabilities);

        UeberCertificateDTO output = resource.getUeberCertificate(owner.getKey());
        assertThat(output)
            .isNotNull()
            .isEqualTo(container);
    }

    @Test
    public void testGetUeberCertificateReturnsNotFoundWhenOwnerLacksCertificate() {
        Owner owner = this.createOwner();
        OwnerResource resource = this.buildOwnerResource();

        assertThrows(NotFoundException.class, () -> resource.getUeberCertificate(owner.getKey()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\t" })
    public void testGetUeberCertificateRequiresValidOwner(String ownerKey) {
        OwnerResource resource = this.buildOwnerResource();

        assertThrows(BadRequestException.class, () -> resource.getUeberCertificate(ownerKey));
    }

    @Test
    public void testGetUeberCertificateReturnsNotFoundWhenOwnerDoesntExist() {
        OwnerResource resource = this.buildOwnerResource();

        assertThrows(NotFoundException.class, () -> resource.getUeberCertificate("404owner"));
    }

}
