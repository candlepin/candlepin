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
package org.candlepin.pki.impl.bc;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.ConfigurationException;
import org.candlepin.model.Consumer;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.SchemeReader;
import org.candlepin.pki.SignatureValidator;
import org.candlepin.pki.Signer;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509CertificateBuilder;
import org.candlepin.pki.impl.jca.JcaSignatureValidator;
import org.candlepin.pki.impl.jca.JcaSigner;
import org.candlepin.util.function.CheckedFunction;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;



/**
 * Implementation of the CryptoManager which uses Bouncy Castle-backed versions of the cryptographic
 * service classes.
 */
@Singleton
public class BouncyCastleCryptoManager implements CryptoManager {
    private static Logger log = LoggerFactory.getLogger(BouncyCastleCryptoManager.class);

    private final BouncyCastleProvider securityProvider;
    private final CertificateReader certreader;
    private final SubjectKeyIdentifierWriter skiWriter;

    private final List<Scheme> schemes;
    private final Scheme defaultScheme;
    private final File upstreamCertificateRepo;

    @Inject
    public BouncyCastleCryptoManager(Configuration config, BouncyCastleProvider securityProvider,
        SchemeReader schemeReader, CertificateReader certreader, SubjectKeyIdentifierWriter skiWriter) {

        Objects.requireNonNull(config);
        Objects.requireNonNull(schemeReader);

        this.securityProvider = Objects.requireNonNull(securityProvider);
        this.certreader = Objects.requireNonNull(certreader);
        this.skiWriter = Objects.requireNonNull(skiWriter);

        this.schemes = Collections.unmodifiableList(schemeReader.readSchemes());
        this.defaultScheme = schemeReader.readDefaultScheme();
        this.validateSchemes();

        this.upstreamCertificateRepo = this.readUpstreamCertificateRepoConfig(config);
    }

    /**
     * Validates the configured schemes by attempting to generate a key and certificate with each, using the
     * configured security provider. If any scheme fails this step, this method throws an exception.
     *
     * @throws ConfigurationException
     *  if any scheme fails validation
     */
    private void validateSchemes() {
        // TODO: validate the schemes
    }

    /**
     * Reads the upstream certificate repo configuration, performing basic validation on the configured path.
     *
     * @param config
     *  the configuration from which to read the configured upstream certificate repo value
     *
     * @throws ConfigurationException
     *  if the configuration value cannot be read or has an invalid value
     *
     * @return
     *  the configured upstream certificate repo value
     */
    private File readUpstreamCertificateRepoConfig(Configuration config) {
        // Attempt to read from candlepin.crypto.upstream_certificate_repo if that fails, fall back to legacy
        // config candlepin.upstream_ca_cert
        String certrepo = config.getOptionalString(ConfigProperties.CRYPTO_UPSTREAM_CERT_REPO)
            .or(() -> config.getOptionalString(ConfigProperties.LEGACY_CA_CERT_UPSTREAM))
            .orElseThrow(() -> new ConfigurationException("Cannot configure upstream certificate repo: " +
                "no configuration value defined @ " + ConfigProperties.CRYPTO_UPSTREAM_CERT_REPO));

        // TODO: We should, instead, add the ability for users to not have an upstream certificate repo at
        // all. As it is now, this is an open attack vector to anyone with the right permissions or ability
        // to drop certs in the upstream repo directory.
        if (certrepo.isBlank()) {
            throw new ConfigurationException("Cannot configure upstream certificate repo: " +
                "repo path cannot be null or empty");
        }

        return new File(certrepo);
    }

    @Override
    public java.security.Provider getSecurityProvider() {
        return this.securityProvider;
    }

    @Override
    public List<Scheme> getCryptoSchemes() {
        // Impl note: this is only safe if we use an immutable list
        return this.schemes;
    }

    @Override
    public Optional<Scheme> getCryptoScheme(String scheme) {
        if (scheme == null) {
            throw new IllegalArgumentException("scheme is null");
        }

        return this.schemes.stream()
            .filter(elem -> scheme.equalsIgnoreCase(elem.name()))
            .findAny();
    }

    // This may not even be needed. It was originally spec'd out to deal with the keygen stuff, but if that's
    // getting refactored anyway, maybe this is extraneous.

    @Override
    public Optional<Scheme> getCryptoScheme(Consumer consumer) {
        // TODO: FIXME: implement this once consumer has been updated to support scheme negotiation
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Scheme getDefaultCryptoScheme() {
        return this.defaultScheme;
    }

    @Override
    public Set<X509Certificate> getUpstreamCertificates() throws CertificateException {
        // If the directory/file doesn't exist, log a warning and return an empty set
        if (!this.upstreamCertificateRepo.exists()) {
            log.debug("Upstream certificate repo does not exist: {}", this.upstreamCertificateRepo);
            return Set.of();
        }

        // If we want to support PKCS12 repos, we'll want to branch on whether or not the file represents
        // a directory or a file object. For now we'll just doubly-validate it to ensure the file system
        // hasn't changed on us since startup.
        if (!this.upstreamCertificateRepo.isDirectory()) {
            throw new CertificateException("upstream certificate repo is not a directory");
        }

        File[] files = this.upstreamCertificateRepo.listFiles();
        if (files == null) {
            throw new CertificateException("Unable to read upstream certificates: " +
                "an exception occurred while reading certificate repo");
        }

        return Stream.of(files)
            .filter(File::isFile)
            .map(CheckedFunction.rethrow(this.certreader::read))
            .collect(Collectors.toSet());
    }

    /**
     * Performs the actual certificate verification operation, checking if the parent and child certificates
     * are the same certificate, or if the child has been signed using the parent cert's private key.
     *
     * @param parent
     *  the parent certificate
     *
     * @param child
     *  the child certificate to verify
     *
     * @param provider
     *  the security provider to handle the verification operation; must be the security provider backing this
     *  crypto manager
     *
     * @return
     *  true if the given child certificate is equal to or signed by the parent certificate; false otherwise
     */
    private boolean verifyCertificate(X509Certificate parent, X509Certificate child,
        java.security.Provider provider) {

        try {
            if (parent.equals(child)) {
                return true;
            }

            child.verify(parent.getPublicKey(), provider);

            // If no exception occurred, the certificate is verified, I guess
            return true;
        }
        catch (InvalidKeyException e) {
            // This is our "expected" failure path for when a cert is not signed by the given private key.
            // Annoying, but we don't want to spam the log a bunch of times while we iterate through many
            // keys.
            log.debug("Certificate validation failed: invalid key", e);
        }
        catch (NoSuchAlgorithmException e) {
            log.warn("Certificate validation failed: signature algorithm not supported", e);
        }
        catch (CertificateException | SignatureException e) {
            log.warn("Certificate validation failed: unexpected encoding error", e);
        }
        catch (UnsupportedOperationException e) {
            // This one shouldn't happen post Java 1.8
            log.warn("Certificate validation failed: verify operation not supported", e);
        }

        return false;
    }

    @Override
    public boolean isTrustedCertificate(X509Certificate certificate) throws CertificateException {
        if (certificate == null) {
            throw new IllegalArgumentException("certificate is null");
        }

        Stream<X509Certificate> schemeCerts = Stream.concat(this.schemes.stream(),
            Stream.of(this.defaultScheme))
            .map(Scheme::certificate);

        Set<X509Certificate> upstreamCerts = this.getUpstreamCertificates();

        return Stream.concat(schemeCerts, upstreamCerts.stream())
            .anyMatch(parent -> this.verifyCertificate(parent, certificate, this.securityProvider));
    }

    @Override
    public Signer getSigner(Scheme scheme) {
        if (scheme == null) {
            throw new IllegalArgumentException("scheme is null");
        }

        if (scheme.privateKey().isEmpty()) {
            throw new IllegalArgumentException("scheme does not include a private key");
        }

        return new JcaSigner(this.securityProvider, scheme);
    }

    @Override
    public SignatureValidator getSignatureValidator(Scheme scheme) {
        if (scheme == null) {
            throw new IllegalArgumentException("scheme is null");
        }

        return new JcaSignatureValidator(this.securityProvider, scheme);
    }

    @Override
    public X509CertificateBuilder getCertificateBuilder(Scheme scheme) {
        if (scheme == null) {
            throw new IllegalArgumentException("scheme is null");
        }

        if (scheme.privateKey().isEmpty()) {
            throw new IllegalArgumentException("scheme does not include a private key");
        }

        return new BouncyCastleX509CertificateBuilder(this.securityProvider, this.skiWriter, scheme);
    }

    // @Override
    // public KeyPairGenerator getKeyPairGenerator(Scheme scheme) {
    //     if (scheme == null) {
    //         throw new IllegalArgumentException("scheme is null");
    //     }

    //     return new BouncyCastleKeyPairGenerator(this.securityProvider, this.keypairDataCurator, scheme);
    // }

}
