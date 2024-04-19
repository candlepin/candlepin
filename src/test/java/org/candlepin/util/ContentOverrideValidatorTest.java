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
package org.candlepin.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.dto.api.server.v1.ContentOverrideDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.ContentOverride;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;


@ExtendWith(MockitoExtension.class)
public class ContentOverrideValidatorTest {

    private DevConfig config;
    private I18n i18n;

    @BeforeEach
    public void setupTest() {
        this.config = TestConfig.defaults();
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
    }

    private ContentOverrideValidator buildValidator() {
        return new ContentOverrideValidator(this.config, this.i18n);
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
        ContentOverrideValidator validator = this.buildValidator();
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        validator.validate(overrides);
    }

    @Test
    public void testValidateWithValidEmptyCollection() {
        ContentOverrideValidator validator = this.buildValidator();

        validator.validate(new LinkedList<>());
    }

    @Test
    public void testValidateWithValidNullCollection() {
        ContentOverrideValidator validator = this.buildValidator();

        validator.validate(null);
    }

    @Test
    public void testValidateWithNullContentLabel() {
        ContentOverrideValidator validator = this.buildValidator();
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel(null)
            .name("test_name-x")
            .value("test_value-x");

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> validator.validate(overrides));
    }

    @Test
    public void testValidateWithEmptyContentLabel() {
        ContentOverrideValidator validator = this.buildValidator();
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("")
            .name("test_name-x")
            .value("test_value-x");

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> validator.validate(overrides));
    }

    @Test
    public void testValidateWithLongContentLabel() {
        ContentOverrideValidator validator = this.buildValidator();
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        validator.validate(overrides);

        // Add our invalid override...
        StringBuilder builder = new StringBuilder();
        while (builder.length() < ContentOverride.MAX_NAME_AND_LABEL_LENGTH) {
            builder.append("longstring");
        }

        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel(builder.toString())
            .name("test_name-x")
            .value("test_value-x");

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> validator.validate(overrides));
    }

    @Test
    public void testValidateWithNullPropertyName() {
        ContentOverrideValidator validator = this.buildValidator();
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name(null)
            .value("test_value-x");

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> validator.validate(overrides));
    }

    @Test
    public void testValidateWithEmptyPropertyName() {
        ContentOverrideValidator validator = this.buildValidator();
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name("")
            .value("test_value-x");

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> validator.validate(overrides));
    }

    @Test
    public void testValidateWithLongPropertyName() {
        ContentOverrideValidator validator = this.buildValidator();
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        validator.validate(overrides);

        // Add our invalid override...
        StringBuilder builder = new StringBuilder();
        extracted(builder);

        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name(builder.toString())
            .value("test_value-x");

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> validator.validate(overrides));
    }

    private static void extracted(StringBuilder builder) {
        while (builder.length() < ContentOverride.MAX_NAME_AND_LABEL_LENGTH) {
            builder.append("longstring");
        }
    }

    /**
     * Property generator for the ValidateWithInvalidPropertyNameStandalone test
     */
    protected static Stream<String> invalidStandaloneProperties() {
        Set<String> properties = ContentOverrideValidator.DEFAULT_BLOCKLIST;

        List<String> output = new LinkedList<>();

        for (String property : properties) {
            output.add(property);
            output.add(property.toUpperCase());
        }

        return output.stream();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "name", "label" })
    public void testValidateRejectsDefaultInvalidPropertyNames(String property) {
        ContentOverrideValidator validator = this.buildValidator();

        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name(property)
            .value("test_value-x");

        // This should fail
        assertThrows(BadRequestException.class, () -> validator.validate(Arrays.asList(invalid)));
    }

    private static List<String> invalidContentOverrideFields() {
        return List.of("field1", "SoMeFieLdNaME", "12345");
    }

    @ParameterizedTest
    @MethodSource("invalidContentOverrideFields")
    public void testValidateRejectsInvalidPropertyNamesFromConfig(String property) {
        // Set our config to include some blocked properties
        String blocklistConfig = String.join(",", invalidContentOverrideFields());
        this.config.setProperty(ConfigProperties.CONTENT_OVERRIDE_BLOCKLIST, blocklistConfig);

        ContentOverrideValidator validator = this.buildValidator();

        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name(property)
            .value("test_value-x");

        assertThrows(BadRequestException.class, () -> validator.validate(List.of(invalid)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "FiELd1", "FIELD2", "field3" })
    public void testValidateRejectsInvalidPropertyNamesFromConfigCaseInsensitive(String property) {
        // Set our config to include some blocked properties
        this.config.setProperty(ConfigProperties.CONTENT_OVERRIDE_BLOCKLIST, "field1, field2, FielD3");

        ContentOverrideValidator validator = this.buildValidator();

        ContentOverrideDTO override = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name(property)
            .value("test_value-x");

        assertThrows(BadRequestException.class, () -> validator.validate(List.of(override)));
    }

    public void testValidateAllowsPropertyNamesNotInBlocklistConfig() {
        List<String> blockedFields = invalidContentOverrideFields();

        // Set our config to include some blocked properties
        String blocklistConfig = String.join(",", blockedFields);
        this.config.setProperty(ConfigProperties.CONTENT_OVERRIDE_BLOCKLIST, blocklistConfig);

        String field = "safe_field";

        // Ensure we picked something that isn't in the blockedFields list
        assertFalse(blockedFields.contains(field));

        ContentOverrideValidator validator = this.buildValidator();

        ContentOverrideDTO override = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name(field)
            .value("test_value-x");

        // This should not fail
        assertDoesNotThrow(() -> validator.validate(List.of(override)));
    }

    @Test
    public void testValidateWithNullOverrideValue() {
        ContentOverrideValidator validator = this.buildValidator();
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name("test_name-x")
            .value(null);

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> validator.validate(overrides));
    }

    @Test
    public void testValidateWithEmptyOverrideValue() {
        ContentOverrideValidator validator = this.buildValidator();
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name("test_name-x")
            .value("");

        overrides.add(invalid);

        // This should fail now
        assertThrows(BadRequestException.class, () -> validator.validate(overrides));
    }

    @Test
    public void testValidateWithValidOverrideValue() {
        ContentOverrideValidator validator = this.buildValidator();

        StringBuilder builder = new StringBuilder();
        String value = "longstring";
        while (builder.length() < ContentOverride.MAX_NAME_AND_LABEL_LENGTH - 100) {
            builder.append(value);
        }

        List<ContentOverrideDTO> overrides = new ArrayList<>();
        ContentOverrideDTO contentOverride = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name("test_name-x")
            .value(builder.toString());

        overrides.add(contentOverride);

        assertDoesNotThrow(() -> validator.validate(overrides));
    }

    @Test
    public void testValidateWithInvalidLongOverrideValue() {
        ContentOverrideValidator validator = this.buildValidator();

        StringBuilder builder = new StringBuilder();
        String value = "longstring";
        while (builder.length() < ContentOverride.MAX_VALUE_LENGTH + 100) {
            builder.append(value);
        }

        List<ContentOverrideDTO> overrides = new ArrayList<>();
        ContentOverrideDTO contentOverride = new ContentOverrideDTO()
            .contentLabel("test_label-x")
            .name("test_name-x")
            .value(builder.toString());

        overrides.add(contentOverride);

        assertThrows(BadRequestException.class, () -> validator.validate(overrides));
    }

}
