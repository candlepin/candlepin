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
package org.candlepin.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.dto.api.server.v1.ContentOverrideDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.ContentOverride;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.test.DatabaseTestFixture;

import org.jboss.resteasy.core.ResteasyContext;
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
import java.util.stream.StreamSupport;



/**
 * ActivationKeyContentOverrideResourceTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("checkstyle:indentation")
public class ActivationKeyContentOverrideResourceTest extends DatabaseTestFixture {

    @Mock
    private Principal principal;

    private ActivationKey key;

    private ActivationKeyResource resource;

    @BeforeEach
    public void setUp() {
        this.key = this.createActivationKey(this.createOwner());

        ResteasyContext.pushContext(Principal.class, principal);

        when(this.principal.canAccess(any(Object.class), any(SubResource.class), any(Access.class)))
            .thenReturn(true);

        this.resource = injector.getInstance(ActivationKeyResource.class);
    }

    private List<ActivationKeyContentOverride> createOverrides(ActivationKey key, int offset,
        int count) {

        List<ActivationKeyContentOverride> overrides = new LinkedList<>();

        for (int i = offset; i < offset + count; ++i) {
            ActivationKeyContentOverride akco = new ActivationKeyContentOverride()
                .setKey(key)
                .setContentLabel("content_label-" + i)
                .setName("override_name-" + i)
                .setValue("override_value-" + i);

            overrides.add(this.activationKeyContentOverrideCurator.create(akco));
        }

        return overrides;
    }

    private long sizeOf(Iterable<ContentOverrideDTO> list) {
        return StreamSupport.stream(list.spliterator(), false).count();
    }

    /**
     * Compares the collections of override DTOs by converting them to generic override lists and
     * stripping their timestamps.
     */
    private void compareOverrideDTOs(List<ContentOverrideDTO> expected, Iterable<ContentOverrideDTO> actual) {
        java.util.function.Consumer<ContentOverrideDTO> preprocessor = override -> {
            if (override == null) {
                return;
            }

            // Strip fields that aren't important for the purposes of determining DTO equality
            override.created(null)
                .updated(null);
        };

        if (expected != null) {
            expected.forEach(preprocessor);
        }

        if (actual != null) {
            actual = StreamSupport.stream(actual.spliterator(), false)
                .peek(preprocessor)
                .toList();
        }

        assertEquals(expected, actual);
    }

    private String getLongString() {
        StringBuilder builder = new StringBuilder();

        while (builder.length() < ContentOverride.MAX_VALUE_LENGTH) {
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
            .toList();

        List<ContentOverrideDTO> actual = this.resource
            .listActivationKeyContentOverrides(key.getId())
            .toList();

        this.compareOverrideDTOs(expected, actual);
    }

    @Test
    public void testGetOverridesEmptyList() {
        List<ContentOverrideDTO> actual = this.resource
            .listActivationKeyContentOverrides(key.getId())
            .toList();

        assertEquals(0, sizeOf(actual));
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
            .toList();

        List<ContentOverrideDTO> actual = this.resource
            .deleteActivationKeyContentOverrides(key.getId(), Arrays.asList(toDeleteDTO))
            .toList();

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
            .deleteActivationKeyContentOverrides(key.getId(), Arrays.asList(toDeleteDTO))
            .toList();

        this.compareOverrideDTOs(expected, actual);
    }

    @Test
    public void testDeleteAllOverridesUsingEmptyList() {
        this.createOverrides(this.key, 1, 3);

        List<ContentOverrideDTO> actual = this.resource
            .deleteActivationKeyContentOverrides(key.getId(), Collections.emptyList())
            .toList();

        assertEquals(0, sizeOf(actual));
    }

    @Test
    public void testDeleteAllOverridesUsingEmptyContentLabel() {
        this.createOverrides(this.key, 1, 3);

        List<ContentOverrideDTO> actual = this.resource
            .deleteActivationKeyContentOverrides(key.getId(), Collections.emptyList())
            .toList();

        assertEquals(0, sizeOf(actual));
    }

    @Test
    public void testAddOverride() {
        List<ContentOverrideDTO> overrides = new LinkedList<>();
        ContentOverrideDTO dto1 = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value");

        overrides.add(dto1);

        List<ContentOverrideDTO> actual = this.resource
            .addActivationKeyContentOverrides(key.getId(), List.of(dto1))
            .toList();

        dto1.source(ActivationKeyContentOverride.DISCRIMINATOR_VALUE);
        this.compareOverrideDTOs(overrides, actual);

        // Add a second to ensure we don't clobber the first
        ContentOverrideDTO dto2 = new ContentOverrideDTO()
            .contentLabel("test_label-2")
            .name("override_name-2")
            .value("override_value-2");

        overrides.add(dto2);

        actual = this.resource
            .addActivationKeyContentOverrides(key.getId(), List.of(dto2))
            .toList();

        dto2.source(ActivationKeyContentOverride.DISCRIMINATOR_VALUE);
        this.compareOverrideDTOs(overrides, actual);
    }

    @Test
    public void testAddOverrideOverwritesExistingWhenMatched() {
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value");

        List<ContentOverrideDTO> actual = this.resource
            .addActivationKeyContentOverrides(key.getId(), List.of(dto))
            .toList();

        dto.source(ActivationKeyContentOverride.DISCRIMINATOR_VALUE);
        this.compareOverrideDTOs(List.of(dto), actual);

        // Add a "new" override that has the same label and name as the first which should inherit
        // the new value
        ContentOverrideDTO update = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value-2")
            .source(ActivationKeyContentOverride.DISCRIMINATOR_VALUE);

        actual = this.resource.addActivationKeyContentOverrides(key.getId(), List.of(update))
            .toList();

        update.source(ActivationKeyContentOverride.DISCRIMINATOR_VALUE);
        this.compareOverrideDTOs(List.of(update), actual);
    }

    @Test
    public void testAddOverrideFailsValidationWithNoParent() {
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("test_label")
            .name("override_name")
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> this.resource.addActivationKeyContentOverrides(null, List.of(dto)));
    }

    @Test
    public void testAddOverrideFailsValidationWithNullLabel() {
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel(null)
            .name("override_name")
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> resource.addActivationKeyContentOverrides(key.getId(), List.of(dto)));
    }

    @Test
    public void testAddOverrideFailsValidationWithEmptyLabel() {
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("")
            .name("override_name")
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> resource.addActivationKeyContentOverrides(key.getId(), List.of(dto)));
    }

    @Test
    public void testAddOverrideFailsValidationWithLongLabel() {
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel(this.getLongString())
            .name("override_name")
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> resource.addActivationKeyContentOverrides(key.getId(), List.of(dto)));
    }

    @Test
    public void testAddOverrideFailsValidationWithNullName() {
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name(null)
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> resource.addActivationKeyContentOverrides(key.getId(), List.of(dto)));
    }

    @Test
    public void testAddOverrideFailsValidationWithEmptyName() {
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("")
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> resource.addActivationKeyContentOverrides(key.getId(), List.of(dto)));
    }

    @Test
    public void testAddOverrideFailsValidationWithLongName() {
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name(this.getLongString())
            .value("override_value");

        assertThrows(BadRequestException.class,
            () -> resource.addActivationKeyContentOverrides(key.getId(), List.of(dto)));
    }

    @Test
    public void testAddOverrideFailsValidationWithNullValue() {
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("override_name")
            .value(null);

        assertThrows(BadRequestException.class,
            () -> resource.addActivationKeyContentOverrides(key.getId(), List.of(dto)));
    }

    @Test
    public void testAddOverrideFailsValidationWithEmptyValue() {
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("override_name")
            .value("");

        assertThrows(BadRequestException.class,
            () -> resource.addActivationKeyContentOverrides(key.getId(), List.of(dto)));
    }

    @Test
    public void testAddOverrideFailsValidationWithLongValue() {
        ContentOverrideDTO dto = new ContentOverrideDTO()
            .contentLabel("content_label")
            .name("override_name")
            .value(this.getLongString());

        assertThrows(BadRequestException.class,
            () -> resource.addActivationKeyContentOverrides(key.getId(), List.of(dto)));
    }
}
