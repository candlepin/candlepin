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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;



/**
 * ActivationKeyContentOverrideResourceTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("checkstyle:indentation")
public class ActivationKeyContentOverrideResourceTest extends DatabaseTestFixture {

    @Mock private Principal principal;
    @Mock private UriInfo context;

    private Owner owner;
    private ActivationKey key;

    private ContentOverrideValidator validator;
    private ActivationKeyContentOverrideResource resource;

    @BeforeEach
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
            .contentLabel(toDelete.getContentLabel())
            .name(toDelete.getName());

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
            .contentLabel(toDelete.getContentLabel());

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
        this.createOverrides(this.key, 1, 3);

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
        this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value");

        overrides.add(dto);

        List<ContentOverrideDTO> actual = this.resource
            .addContentOverrides(context, principal, overrides)
            .list();

        this.compareOverrideDTOs(overrides, actual);

        // Add a second to ensure we don't clobber the first
        dto = new ContentOverrideDTO()
            .contentLabel("test_label-2")
            .name("override_name-2")
            .value("override_value-2");

        overrides.add(dto);

        actual = this.resource.addContentOverrides(context, principal, Arrays.asList(dto)).list();

        this.compareOverrideDTOs(overrides, actual);
    }

    @Test
    public void testAddOverrideOverwritesExistingWhenMatched() {
        this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value");

        overrides.add(dto);

        List<ContentOverrideDTO> actual = this.resource
            .addContentOverrides(context, principal, overrides)
            .list();

        this.compareOverrideDTOs(overrides, actual);

        // Add a "new" override that has the same label and name as the first which should inherit
        // the new value
        dto = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value-2");

        overrides.clear();
        overrides.add(dto);

        actual = this.resource.addContentOverrides(context, principal, overrides).list();

        this.compareOverrideDTOs(overrides, actual);
    }

    @Test
    public void testAddOverrideFailsValidationWithNoParent() {
        this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value");

        overrides.add(dto);

        List<ContentOverrideDTO> actual = this.resource
            .addContentOverrides(context, principal, overrides)
            .list();

        this.compareOverrideDTOs(overrides, actual);
    }

    @Test
    public void testAddOverrideFailsValidationWithNullLabel() {
        this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel(null)
            .name("override_name")
            .value("override_value");

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            resource.addContentOverrides(context, principal, overrides).list()
        );
    }

    @Test
    public void testAddOverrideFailsValidationWithEmptyLabel() {
        this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("")
            .name("override_name")
            .value("override_value");

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            resource.addContentOverrides(context, principal, overrides).list()
        );
    }

    @Test
    public void testAddOverrideFailsValidationWithLongLabel() {
        this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel(this.getLongString())
            .name("override_name")
            .value("override_value");

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            resource.addContentOverrides(context, principal, overrides).list()
        );
    }

    @Test
    public void testAddOverrideFailsValidationWithNullName() {
        this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name(null)
            .value("override_value");

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            resource.addContentOverrides(context, principal, overrides).list()
        );
    }

    @Test
    public void testAddOverrideFailsValidationWithEmptyName() {
        this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("")
            .value("override_value");

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            resource.addContentOverrides(context, principal, overrides).list()
        );
    }

    @Test
    public void testAddOverrideFailsValidationWithLongName() {
        this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name(this.getLongString())
            .value("override_value");

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            resource.addContentOverrides(context, principal, overrides).list()
        );
    }

    @Test
    public void testAddOverrideFailsValidationWithNullValue() {
        this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("override_name")
            .value(null);

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            resource.addContentOverrides(context, principal, overrides).list()
        );
    }

    @Test
    public void testAddOverrideFailsValidationWithEmptyValue() {
        this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("override_name")
            .value("");

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            resource.addContentOverrides(context, principal, overrides).list()
        );
    }

    @Test
    public void testAddOverrideFailsValidationWithLongValue() {
        this.modelTranslator.translate(this.key, ActivationKeyDTO.class);

        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("override_name")
            .value(this.getLongString());

        overrides.add(dto);

        assertThrows(BadRequestException.class, () ->
            resource.addContentOverrides(context, principal, overrides).list()
        );
    }
}
