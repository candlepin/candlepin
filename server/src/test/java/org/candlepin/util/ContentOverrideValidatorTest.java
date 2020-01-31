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
package org.candlepin.util;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.api.v1.ContentOverrideDTO;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * ContentOverrideValidatorTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ContentOverrideValidatorTest extends DatabaseTestFixture  {

    @Mock private Configuration config;
    private ContentOverrideValidator validator;

    @BeforeEach
    public void setupTest() {
        this.validator = new ContentOverrideValidator(this.config, this.i18n);
    }

    private List<ContentOverrideDTO> buildOverridesList(int count) {
        List<ContentOverrideDTO> overrides = new LinkedList<>();

        for (int i = 0; i < count; ++i) {
            overrides.add(new ContentOverrideDTO()
                .contentLabel("test_label-" + i)
                .name("test_name-" + i)
                .value("test_value-" + i));
        }

        return overrides;
    }

    @Test
    public void testValidateWithValidCollection() {
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        this.validator.validate(overrides);
    }

    @Test
    public void testValidateWithValidEmptyCollection() {
        this.validator.validate(new LinkedList<>());
    }

    @Test
    public void testValidateWithValidNullCollection() {
        this.validator.validate(null);
    }

    @Test
    public void testValidateWithNullContentLabel() {
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        this.validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel(null)
            .name("test_name-x")
            .value("test_value-x");

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> this.validator.validate(overrides));
    }

    @Test
    public void testValidateWithEmptyContentLabel() {
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        this.validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("")
            .name("test_name-x")
            .value("test_value-x");

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> this.validator.validate(overrides));
    }

    @Test
    public void testValidateWithLongContentLabel() {
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        this.validator.validate(overrides);

        // Add our invalid override...
        StringBuilder builder = new StringBuilder();
        while (builder.length() < ContentOverrideValidator.MAX_VALUE_LENGTH) {
            builder.append("longstring");
        }

        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel(builder.toString())
            .name("test_name-x")
            .value("test_value-x");

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> this.validator.validate(overrides));
    }

    @Test
    public void testValidateWithNullPropertyName() {
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        this.validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name(null)
            .value("test_value-x");

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> this.validator.validate(overrides));
    }

    @Test
    public void testValidateWithEmptyPropertyName() {
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        this.validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name("")
            .value("test_value-x");

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> this.validator.validate(overrides));
    }

    @Test
    public void testValidateWithLongPropertyName() {
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        this.validator.validate(overrides);

        // Add our invalid override...
        StringBuilder builder = new StringBuilder();
        while (builder.length() < ContentOverrideValidator.MAX_VALUE_LENGTH) {
            builder.append("longstring");
        }

        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name(builder.toString())
            .value("test_value-x");

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> this.validator.validate(overrides));
    }

    /**
     * Property generator for the ValidateWithInvalidPropertyNameStandalone test
     */
    protected static Stream<String> invalidStandaloneProperties() {
        Set<String> properties = ContentOverrideValidator.DEFAULT_BLACKLIST;

        List<String> output = new LinkedList<>();

        for (String property : properties) {
            output.add(property);
            output.add(property.toUpperCase());
        }

        return output.stream();
    }

    @ParameterizedTest
    @MethodSource("invalidStandaloneProperties")
    public void testValidateWithInvalidPropertyNameStandalone(String property) {
        // Set our config mock to look like it's in standalone mode
        when(this.config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(true);
        when(this.config.getBoolean(eq(ConfigProperties.STANDALONE), anyBoolean())).thenReturn(true);

        ContentOverrideValidator validator = new ContentOverrideValidator(this.config, this.i18n);

        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name(property)
            .value("test_value-x");

        // This should fail
        assertThrows(BadRequestException.class, () -> this.validator.validate(Arrays.asList(invalid)));
    }

    /**
     * Property generator for the ValidateWithInvalidPropertyNameHosted test
     */
    protected static Stream<String> invalidHostedProperties() {
        Set<String> properties = ContentOverrideValidator.HOSTED_BLACKLIST;

        List<String> output = new LinkedList<>();

        for (String property : properties) {
            output.add(property);
            output.add(property.toUpperCase());
        }

        return output.stream();
    }

    @ParameterizedTest
    @MethodSource("invalidHostedProperties")
    public void testValidateWithInvalidPropertyNameHosted(String property) {
        // Set our config mock to look like it's in standalone mode
        when(this.config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(false);
        when(this.config.getBoolean(eq(ConfigProperties.STANDALONE), anyBoolean())).thenReturn(false);

        ContentOverrideValidator validator = new ContentOverrideValidator(this.config, this.i18n);

        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name(property)
            .value("test_value-x");

        // This should fail
        assertThrows(BadRequestException.class, () -> this.validator.validate(Arrays.asList(invalid)));
    }

    @Test
    public void testValidateWithNullOverrideValue() {
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        this.validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name("test_name-x")
            .value(null);

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> this.validator.validate(overrides));
    }

    @Test
    public void testValidateWithEmptyOverrideValue() {
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        this.validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name("test_name-x")
            .value("");

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> this.validator.validate(overrides));
    }

    @Test
    public void testValidateWithLongOverrideValue() {
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        this.validator.validate(overrides);

        // Add our invalid override...
        StringBuilder builder = new StringBuilder();
        while (builder.length() < ContentOverrideValidator.MAX_VALUE_LENGTH) {
            builder.append("longstring");
        }

        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name("test_name-x")
            .value(builder.toString());

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> this.validator.validate(overrides));
    }
}
