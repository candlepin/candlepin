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
package org.candlepin.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.pki.CryptographyStatusProvider.SchemeMetadata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;

@ExtendWith(MockitoExtension.class)
public class CryptographyStatusProviderTest {

    @Mock
    private Configuration config;

    @Test
    public void testConstructorThrowsOnNullConfig() {
        assertThatThrownBy(() -> new CryptographyStatusProvider(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testHasSchemesReturnsFalseWhenNoSchemesConfigured() {
        CryptographyStatusProvider provider = new CryptographyStatusProvider(config);

        assertThat(provider.hasSchemes())
            .isFalse();
    }

    @Test
    public void testHasSchemesReturnsFalseWhenConfigThrows() {
        when(config.getList(ConfigProperties.CRYPTO_SCHEMES))
            .thenThrow(new NoSuchElementException());

        CryptographyStatusProvider provider = new CryptographyStatusProvider(config);

        assertThat(provider.hasSchemes())
            .isFalse();
    }

    @Test
    public void testHasSchemesReturnsTrueWhenSchemesConfigured() {
        when(config.getList(ConfigProperties.CRYPTO_SCHEMES)).thenReturn(List.of("rsa"));
        setupSchemeConfig("rsa", "SHA256withRSA", "RSA", 4096);

        CryptographyStatusProvider provider = new CryptographyStatusProvider(config);

        assertThat(provider.hasSchemes())
            .isTrue();
    }

    @Test
    public void testGetSupportedSchemesReturnsEmptyListWhenNoSchemesConfigured() {
        CryptographyStatusProvider provider = new CryptographyStatusProvider(config);

        List<SchemeMetadata> schemes = provider.getSupportedSchemes();

        assertThat(schemes)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetSupportedSchemesReturnsSingleScheme() {
        when(config.getList(ConfigProperties.CRYPTO_SCHEMES)).thenReturn(List.of("rsa"));
        setupSchemeConfig("rsa", "SHA256withRSA", "RSA", 4096);

        CryptographyStatusProvider provider = new CryptographyStatusProvider(config);

        assertThat(provider.getSupportedSchemes())
            .singleElement()
            .returns("rsa", SchemeMetadata::name)
            .returns("SHA256withRSA", SchemeMetadata::signatureAlgorithm)
            .returns("RSA", SchemeMetadata::keyAlgorithm)
            .returns(4096, SchemeMetadata::keySize);
    }

    @Test
    public void testGetSupportedSchemesReturnsMultipleSchemesInOrder() {
        when(config.getList(ConfigProperties.CRYPTO_SCHEMES)).thenReturn(List.of("mldsa", "rsa"));
        setupSchemeConfig("mldsa", "ML-DSA-87", "ML-DSA", null);
        setupSchemeConfig("rsa", "SHA256withRSA", "RSA", 4096);

        CryptographyStatusProvider provider = new CryptographyStatusProvider(config);

        List<SchemeMetadata> schemes = provider.getSupportedSchemes();

        assertThat(schemes)
            .extracting(SchemeMetadata::name)
            .containsExactlyInAnyOrder("mldsa", "rsa");
    }

    @Test
    public void testGetSupportedSchemesHandlesSchemeWithoutKeySize() {
        when(config.getList(ConfigProperties.CRYPTO_SCHEMES)).thenReturn(List.of("mldsa"));
        setupSchemeConfig("mldsa", "ML-DSA-87", "ML-DSA", null);

        CryptographyStatusProvider provider = new CryptographyStatusProvider(config);

        List<SchemeMetadata> schemes = provider.getSupportedSchemes();

        assertThat(schemes)
            .singleElement()
            .returns("mldsa", SchemeMetadata::name)
            .returns(null, SchemeMetadata::keySize);
    }

    @Test
    public void testGetSupportedSchemesSkipsBlankSchemeNames() {
        when(config.getList(ConfigProperties.CRYPTO_SCHEMES)).thenReturn(List.of("rsa", "", "  "));
        setupSchemeConfig("rsa", "SHA256withRSA", "RSA", 4096);

        CryptographyStatusProvider provider = new CryptographyStatusProvider(config);

        List<SchemeMetadata> schemes = provider.getSupportedSchemes();

        assertThat(schemes)
            .singleElement()
            .returns("rsa", SchemeMetadata::name);
    }

    @Test
    public void testGetDefaultSchemeNameReturnsConfiguredValue() {
        when(config.getString(ConfigProperties.CRYPTO_DEFAULT_SCHEME)).thenReturn("rsa");

        CryptographyStatusProvider provider = new CryptographyStatusProvider(config);

        assertThat(provider.getDefaultSchemeName())
            .isEqualTo("rsa");
    }

    @Test
    public void testGetDefaultSchemeNameReturnsNullWhenNotConfigured() {
        CryptographyStatusProvider provider = new CryptographyStatusProvider(config);

        assertThat(provider.getDefaultSchemeName())
            .isNull();
    }

    @Test
    public void testSchemeMetadataRecordValidation() {
        SchemeMetadata valid = new SchemeMetadata("test", "sig", "key", 2048);

        assertThat(valid)
            .returns("test", SchemeMetadata::name)
            .returns("sig", SchemeMetadata::signatureAlgorithm)
            .returns("key", SchemeMetadata::keyAlgorithm)
            .returns(2048, SchemeMetadata::keySize);

        // Null keySize is valid
        SchemeMetadata withoutKeySize = new SchemeMetadata("test", "sig", "key", null);

        assertThat(withoutKeySize)
            .returns(null, SchemeMetadata::keySize);
    }

    @Test
    public void testSchemeMetadataAllowsNullAlgorithms() {
        SchemeMetadata scheme = new SchemeMetadata("test", null, null, null);

        assertThat(scheme)
            .returns("test", SchemeMetadata::name)
            .returns(null, SchemeMetadata::signatureAlgorithm)
            .returns(null, SchemeMetadata::keyAlgorithm);

    }

    @Test
    public void testGetSupportedSchemesReturnsUnmodifiableList() {
        when(config.getList(ConfigProperties.CRYPTO_SCHEMES)).thenReturn(List.of("rsa"));
        setupSchemeConfig("rsa", "SHA256withRSA", "RSA", 4096);

        CryptographyStatusProvider provider = new CryptographyStatusProvider(config);
        List<SchemeMetadata> schemes = provider.getSupportedSchemes();

        assertThatThrownBy(() -> schemes.add(new SchemeMetadata("test", "sig", "key", null)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Helper method to set up scheme configuration in the mock.
     */
    private void setupSchemeConfig(String schemeName, String sigAlgo, String keyAlgo, Integer keySize) {
        String sigAlgoKey = ConfigProperties.schemeConfig(schemeName,
            ConfigProperties.CRYPTO_SCHEME_SIGNATURE_ALGORITHM);
        String keyAlgoKey = ConfigProperties.schemeConfig(schemeName,
            ConfigProperties.CRYPTO_SCHEME_KEY_ALGORITHM);
        String keySizeKey = ConfigProperties.schemeConfig(schemeName,
            ConfigProperties.CRYPTO_SCHEME_KEY_SIZE);

        if (sigAlgo != null) {
            when(config.getString(sigAlgoKey)).thenReturn(sigAlgo);
        }
        else {
            when(config.getString(sigAlgoKey)).thenThrow(new NoSuchElementException());
        }

        if (keyAlgo != null) {
            when(config.getString(keyAlgoKey)).thenReturn(keyAlgo);
        }
        else {
            when(config.getString(keyAlgoKey)).thenThrow(new NoSuchElementException());
        }

        if (keySize != null) {
            when(config.getInt(keySizeKey)).thenReturn(keySize);
        }
        else {
            when(config.getInt(keySizeKey)).thenThrow(new NoSuchElementException());
        }
    }
}
