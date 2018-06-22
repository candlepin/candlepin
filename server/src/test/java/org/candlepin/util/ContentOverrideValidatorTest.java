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

import static org.mockito.Mockito.*;
import static org.mockito.Matchers.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.api.v1.ContentOverrideDTO;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;



/**
 * ContentOverrideValidatorTest
 */
@RunWith(JUnitParamsRunner.class)
public class ContentOverrideValidatorTest extends DatabaseTestFixture  {

    private Configuration config;
    private ContentOverrideValidator validator;

    @Before
    public void setupTest() {
        this.config = mock(Configuration.class);
        this.validator = new ContentOverrideValidator(this.config, this.i18n);
    }

    private List<ContentOverrideDTO> buildOverridesList(int count) {
        List<ContentOverrideDTO> overrides = new LinkedList<>();

        for (int i = 0; i < count; ++i) {
            overrides.add(new ContentOverrideDTO()
                .setContentLabel("test_label-" + i)
                .setName("test_name-" + i)
                .setValue("test_value-" + i));
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

    @Test(expected = BadRequestException.class)
    public void testValidateWithNullContentLabel() {
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        this.validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .setContentLabel(null)
            .setName("test_name-x")
            .setValue("test_value-x");

        overrides.add(invalid);

        // This should fail now
        this.validator.validate(overrides);
    }

    @Test(expected = BadRequestException.class)
    public void testValidateWithEmptyContentLabel() {
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        this.validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .setContentLabel("")
            .setName("test_name-x")
            .setValue("test_value-x");

        overrides.add(invalid);

        // This should fail now
        this.validator.validate(overrides);
    }

    @Test(expected = BadRequestException.class)
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
            .setContentLabel(builder.toString())
            .setName("test_name-x")
            .setValue("test_value-x");

        overrides.add(invalid);

        // This should fail now
        this.validator.validate(overrides);
    }

    @Test(expected = BadRequestException.class)
    public void testValidateWithNullPropertyName() {
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        this.validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .setContentLabel("test_label-x")
            .setName(null)
            .setValue("test_value-x");

        overrides.add(invalid);

        // This should fail now
        this.validator.validate(overrides);
    }

    @Test(expected = BadRequestException.class)
    public void testValidateWithEmptyPropertyName() {
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        this.validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .setContentLabel("test_label-x")
            .setName("")
            .setValue("test_value-x");

        overrides.add(invalid);

        // This should fail now
        this.validator.validate(overrides);
    }

    @Test(expected = BadRequestException.class)
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
            .setContentLabel("test_label-x")
            .setName(builder.toString())
            .setValue("test_value-x");

        overrides.add(invalid);

        // This should fail now
        this.validator.validate(overrides);
    }

    /**
     * Property generator for the ValidateWithInvalidPropertyNameStandalone test
     */
    protected Object[] invalidStandaloneProperties() {
        Set<String> properties = ContentOverrideValidator.DEFAULT_BLACKLIST;

        List<Object[]> output = new LinkedList<>();

        for (String property : properties) {
            output.add(new Object[] { property });
            output.add(new Object[] { property.toUpperCase() });
        }

        return output.toArray();
    }

    @Test(expected = BadRequestException.class)
    @Parameters(method = "invalidStandaloneProperties")
    public void testValidateWithInvalidPropertyNameStandalone(String property) {
        // Set our config mock to look like it's in standalone mode
        when(this.config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(true);
        when(this.config.getBoolean(eq(ConfigProperties.STANDALONE), anyBoolean())).thenReturn(true);

        ContentOverrideValidator validator = new ContentOverrideValidator(this.config, this.i18n);

        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .setContentLabel("test_label-x")
            .setName(property)
            .setValue("test_value-x");

        // This should fail
        validator.validate(Arrays.asList(invalid));
    }

    /**
     * Property generator for the ValidateWithInvalidPropertyNameHosted test
     */
    protected Object[] invalidHostedProperties() {
        Set<String> properties = ContentOverrideValidator.HOSTED_BLACKLIST;

        List<Object[]> output = new LinkedList<>();

        for (String property : properties) {
            output.add(new Object[] { property });
            output.add(new Object[] { property.toUpperCase() });
        }

        return output.toArray();
    }

    @Test(expected = BadRequestException.class)
    @Parameters(method = "invalidHostedProperties")
    public void testValidateWithInvalidPropertyNameHosted(String property) {
        // Set our config mock to look like it's in standalone mode
        when(this.config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(false);
        when(this.config.getBoolean(eq(ConfigProperties.STANDALONE), anyBoolean())).thenReturn(false);

        ContentOverrideValidator validator = new ContentOverrideValidator(this.config, this.i18n);

        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .setContentLabel("test_label-x")
            .setName(property)
            .setValue("test_value-x");

        // This should fail
        validator.validate(Arrays.asList(invalid));
    }

    @Test(expected = BadRequestException.class)
    public void testValidateWithNullOverrideValue() {
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        this.validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .setContentLabel("test_label-x")
            .setName("test_name-x")
            .setValue(null);

        overrides.add(invalid);

        // This should fail now
        this.validator.validate(overrides);
    }

    @Test(expected = BadRequestException.class)
    public void testValidateWithEmptyOverrideValue() {
        List<ContentOverrideDTO> overrides = this.buildOverridesList(3);

        // We expect this invocation to pass
        this.validator.validate(overrides);

        // Add our invalid override...
        ContentOverrideDTO invalid = new ContentOverrideDTO()
            .setContentLabel("test_label-x")
            .setName("test_name-x")
            .setValue("");

        overrides.add(invalid);

        // This should fail now
        this.validator.validate(overrides);
    }

    @Test(expected = BadRequestException.class)
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
            .setContentLabel("test_label-x")
            .setName("test_name-x")
            .setValue(builder.toString());

        overrides.add(invalid);

        // This should fail now
        this.validator.validate(overrides);
    }
}
