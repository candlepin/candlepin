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

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.dto.api.v1.ActivationKeyDTO;
import org.candlepin.dto.api.v1.ContentOverrideDTO;
import org.candlepin.model.Owner;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.ContentOverrideValidator;

import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;



/**
 * ActivationKeyContentOverrideResourceTest
 */
@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("checkstyle:indentation")
public class ActivationKeyContentOverrideResourceTest extends DatabaseTestFixture {

    @Mock private Principal principal;
    @Mock private UriInfo context;

    private Owner owner;
    private ActivationKey key;

    private ContentOverrideValidator validator;
    private ActivationKeyContentOverrideResource resource;

    @Before
    public void setUp() {
        this.owner = this.createOwner();
        this.key = this.createActivationKey(owner);

        MultivaluedMap<String, String> mvm = new MultivaluedMapImpl<>();
        mvm.add("activation_key_id", key.getId());

        when(this.context.getPathParameters()).thenReturn(mvm);

        when(this.principal.canAccess(any(Object.class), any(SubResource.class), any(Access.class)))
            .thenReturn(true);

        this.validator = new ContentOverrideValidator(this.config, this.i18n);

        this.resource = new ActivationKeyContentOverrideResource(this.i18n,
            this.activationKeyContentOverrideCurator, this.activationKeyCurator, this.validator,
            this.modelTranslator);
    }

    private List<ActivationKeyContentOverride> createOverrides(ActivationKey key, int offset,
        int count) {

        List<ActivationKeyContentOverride> overrides = new LinkedList<>();

        for (int i = offset; i < offset + count; ++i) {
            ActivationKeyContentOverride akco = new ActivationKeyContentOverride();
            akco.setKey(key);
            akco.setContentLabel("content_label-" + i);
            akco.setName("override_name-" + i);
            akco.setValue("override_value-" + i);

            overrides.add(this.activationKeyContentOverrideCurator.create(akco));
        }

        return overrides;
    }

    /**
     * Removes the created and updated timestamps from the DTOs to make comparison easier
     */
    private List<ContentOverrideDTO> stripTimestamps(List<ContentOverrideDTO> list) {
        if (list != null) {
            for (ContentOverrideDTO dto : list) {
                dto.setCreated(null);
                dto.setUpdated(null);
            }
        }

        return list;
    }

    /**
     * Compares the collections of override DTOs by converting them to generic override lists and
     * stripping their timestamps.
     */
    private void compareOverrideDTOs(List<ContentOverrideDTO> expected, List<ContentOverrideDTO> actual) {
        assertEquals(this.stripTimestamps(expected), this.stripTimestamps(actual));
    }

    private String getLongString() {
        StringBuilder builder = new StringBuilder();

        while (builder.length() < ContentOverrideValidator.MAX_VALUE_LENGTH) {
            builder.append("longstring");
        }

        return builder.toString();
    }

    @Test
    public void testGetOverrides() {
        List<ActivationKeyContentOverride> overrides = this.createOverrides(this.key, 1, 3);

        List<ContentOverrideDTO> expected = overrides.stream()
            .map(this.modelTranslator.getStreamMapper(ActivationKeyContentOverride.class,
                ContentOverrideDTO.class))
            .collect(Collectors.toList());

        List<ContentOverrideDTO> actual = this.resource
            .getContentOverrideList(context, principal)
            .list();

        this.compareOverrideDTOs(expected, actual);
    }

    @Test
    public void testGetOverridesEmptyList() {
        List<ContentOverrideDTO> actual = this.resource
            .getContentOverrideList(context, principal)
            .list();

        assertEquals(0, actual.size());
    }

    @Test
    public void testDeleteOverrideUsingName() {
        List<ActivationKeyContentOverride> overrides = this.createOverrides(this.key, 1, 3);

        ActivationKeyContentOverride toDelete = overrides.remove(1);
        ContentOverrideDTO toDeleteDTO = new ContentOverrideDTO()
            .setContentLabel(toDelete.getContentLabel())
            .setName(toDelete.getName());

        List<ContentOverrideDTO> expected = overrides.stream()
            .map(this.modelTranslator.getStreamMapper(ActivationKeyContentOverride.class,
                ContentOverrideDTO.class))
            .collect(Collectors.toList());

        List<ContentOverrideDTO> actual = this.resource
            .deleteContentOverrides(context, principal, Arrays.asList(toDeleteDTO))
            .list();

        this.compareOverrideDTOs(expected, actual);
    }

    @Test
    public void testDeleteOverridesUsingContentLabel() {
        List<ActivationKeyContentOverride> overrides = this.createOverrides(this.key, 1, 3);

        ActivationKeyContentOverride toDelete = overrides.remove(1);
        ContentOverrideDTO toDeleteDTO = new ContentOverrideDTO()
            .setContentLabel(toDelete.getContentLabel());

        List<ContentOverrideDTO> expected = overrides.stream()
            .map(this.modelTranslator.getStreamMapper(ActivationKeyContentOverride.class,
                ContentOverrideDTO.class))
            .collect(Collectors.toList());

        List<ContentOverrideDTO> actual = this.resource
            .deleteContentOverrides(context, principal, Arrays.asList(toDeleteDTO))
            .list();

        this.compareOverrideDTOs(expected, actual);
    }

    @Test
    public void testDeleteAllOverridesUsingEmptyList() {
        List<ActivationKeyContentOverride> overrides = this.createOverrides(this.key, 1, 3);

        List<ContentOverrideDTO> actual = this.resource
            .deleteContentOverrides(context, principal, Collections.emptyList())
            .list();

        assertEquals(0, actual.size());
    }

    @Test
    public void testDeleteAllOverridesUsingEmptyContentLabel() {
        List<ActivationKeyContentOverride> overrides = this.createOverrides(this.key, 1, 3);

        List<ContentOverrideDTO> actual = this.resource
            .deleteContentOverrides(context, principal, Collections.emptyList())
            .list();

        assertEquals(0, actual.size());
    }

    @Test
    public void testAddOverride() {
        ActivationKeyDTO kdto = this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .setContentLabel("test_label")
            .setName("override_name")
            .setValue("override_value");

        overrides.add(dto);

        List<ContentOverrideDTO> actual = this.resource
            .addContentOverrides(context, principal, overrides)
            .list();

        this.compareOverrideDTOs(overrides, actual);

        // Add a second to ensure we don't clobber the first
        dto = new ContentOverrideDTO()
            .setContentLabel("test_label-2")
            .setName("override_name-2")
            .setValue("override_value-2");

        overrides.add(dto);

        actual = this.resource.addContentOverrides(context, principal, Arrays.asList(dto)).list();

        this.compareOverrideDTOs(overrides, actual);
    }

    @Test
    public void testAddOverrideOverwritesExistingWhenMatched() {
        ActivationKeyDTO kdto = this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .setContentLabel("test_label")
            .setName("override_name")
            .setValue("override_value");

        overrides.add(dto);

        List<ContentOverrideDTO> actual = this.resource
            .addContentOverrides(context, principal, overrides)
            .list();

        this.compareOverrideDTOs(overrides, actual);

        // Add a "new" override that has the same label and name as the first which should inherit
        // the new value
        dto = new ContentOverrideDTO()
            .setContentLabel("test_label")
            .setName("override_name")
            .setValue("override_value-2");

        overrides.clear();
        overrides.add(dto);

        actual = this.resource.addContentOverrides(context, principal, overrides).list();

        this.compareOverrideDTOs(overrides, actual);
    }

    @Test
    public void testAddOverrideFailsValidationWithNoParent() {
        ActivationKeyDTO kdto = this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .setContentLabel("test_label")
            .setName("override_name")
            .setValue("override_value");

        overrides.add(dto);

        List<ContentOverrideDTO> actual = this.resource
            .addContentOverrides(context, principal, overrides)
            .list();

        this.compareOverrideDTOs(overrides, actual);
    }

    @Test(expected = BadRequestException.class)
    public void testAddOverrideFailsValidationWithNullLabel() {
        ActivationKeyDTO kdto = this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .setContentLabel(null)
            .setName("override_name")
            .setValue("override_value");

        overrides.add(dto);

        List<ContentOverrideDTO> actual = this.resource
            .addContentOverrides(context, principal, overrides)
            .list();
    }

    @Test(expected = BadRequestException.class)
    public void testAddOverrideFailsValidationWithEmptyLabel() {
        ActivationKeyDTO kdto = this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .setContentLabel("")
            .setName("override_name")
            .setValue("override_value");

        overrides.add(dto);

        List<ContentOverrideDTO> actual = this.resource
            .addContentOverrides(context, principal, overrides)
            .list();
    }

    @Test(expected = BadRequestException.class)
    public void testAddOverrideFailsValidationWithLongLabel() {
        ActivationKeyDTO kdto = this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .setContentLabel(this.getLongString())
            .setName("override_name")
            .setValue("override_value");

        overrides.add(dto);

        List<ContentOverrideDTO> actual = this.resource
            .addContentOverrides(context, principal, overrides)
            .list();
    }

    @Test(expected = BadRequestException.class)
    public void testAddOverrideFailsValidationWithNullName() {
        ActivationKeyDTO kdto = this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .setContentLabel("content_label")
            .setName(null)
            .setValue("override_value");

        overrides.add(dto);

        List<ContentOverrideDTO> actual = this.resource
            .addContentOverrides(context, principal, overrides)
            .list();
    }

    @Test(expected = BadRequestException.class)
    public void testAddOverrideFailsValidationWithEmptyName() {
        ActivationKeyDTO kdto = this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .setContentLabel("content_label")
            .setName("")
            .setValue("override_value");

        overrides.add(dto);

        List<ContentOverrideDTO> actual = this.resource
            .addContentOverrides(context, principal, overrides)
            .list();
    }

    @Test(expected = BadRequestException.class)
    public void testAddOverrideFailsValidationWithLongName() {
        ActivationKeyDTO kdto = this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .setContentLabel("content_label")
            .setName(this.getLongString())
            .setValue("override_value");

        overrides.add(dto);

        List<ContentOverrideDTO> actual = this.resource
            .addContentOverrides(context, principal, overrides)
            .list();
    }

    @Test(expected = BadRequestException.class)
    public void testAddOverrideFailsValidationWithNullValue() {
        ActivationKeyDTO kdto = this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .setContentLabel("content_label")
            .setName("override_name")
            .setValue(null);

        overrides.add(dto);

        List<ContentOverrideDTO> actual = this.resource
            .addContentOverrides(context, principal, overrides)
            .list();
    }

    @Test(expected = BadRequestException.class)
    public void testAddOverrideFailsValidationWithEmptyValue() {
        ActivationKeyDTO kdto = this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .setContentLabel("content_label")
            .setName("override_name")
            .setValue("");

        overrides.add(dto);

        List<ContentOverrideDTO> actual = this.resource
            .addContentOverrides(context, principal, overrides)
            .list();
    }

    @Test(expected = BadRequestException.class)
    public void testAddOverrideFailsValidationWithLongValue() {
        ActivationKeyDTO kdto = this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .setContentLabel("content_label")
            .setName("override_name")
            .setValue(this.getLongString());

        overrides.add(dto);

        List<ContentOverrideDTO> actual = this.resource
            .addContentOverrides(context, principal, overrides)
            .list();
    }
}
