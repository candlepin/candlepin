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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;



public class OwnerContentResourceTest extends DatabaseTestFixture {

    private OwnerContentResource ownerContentResource;

    @BeforeEach
    @Override
    public void init() throws Exception {
        super.init();
        ownerContentResource = injector.getInstance(OwnerContentResource.class);
    }

    @Test
    public void testGetContentsByOwnerFetchesAllVisibleEntities() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Content content1 = TestUtil.createContent("test_content-1", "test_content-1")
            .setNamespace((Owner) null);
        Content content2 = TestUtil.createContent("test_content-2", "test_content-2")
            .setNamespace(owner1.getKey());
        Content content3 = TestUtil.createContent("test_content-3", "test_content-3")
            .setNamespace(owner2.getKey());

        this.createContent(content1);
        this.createContent(content2);
        this.createContent(content3);

        ContentDTO cdto1 = this.modelTranslator.translate(content1, ContentDTO.class);
        ContentDTO cdto2 = this.modelTranslator.translate(content2, ContentDTO.class);

        Stream<ContentDTO> response = this.ownerContentResource
            .getContentsByOwner(owner1.getKey(), List.of(), false);

        assertNotNull(response);
        assertThat(response.toList())
            .hasSize(2)
            .containsOnly(cdto1, cdto2);
    }

    @Test
    public void testGetContentsByOwnerOmitsGlobalsWhenOmitFlagIsSet() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Content content1 = TestUtil.createContent("test_content-1", "test_content-1")
            .setNamespace((Owner) null);
        Content content2 = TestUtil.createContent("test_content-2", "test_content-2")
            .setNamespace(owner1.getKey());
        Content content3 = TestUtil.createContent("test_content-3", "test_content-3")
            .setNamespace(owner2.getKey());

        this.createContent(content1);
        this.createContent(content2);
        this.createContent(content3);

        ContentDTO cdto2 = this.modelTranslator.translate(content2, ContentDTO.class);

        Stream<ContentDTO> response = this.ownerContentResource
            .getContentsByOwner(owner1.getKey(), List.of(), true);

        assertNotNull(response);
        assertThat(response.toList())
            .hasSize(1)
            .containsOnly(cdto2);
    }

    @Test
    public void testGetContentsByOwnerWithEntityIDFiltering() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Content content1 = TestUtil.createContent("test_content-1", "test_content-1")
            .setNamespace((Owner) null);
        Content content2 = TestUtil.createContent("test_content-2", "test_content-2")
            .setNamespace(owner1.getKey());
        Content content3 = TestUtil.createContent("test_content-3", "test_content-3")
            .setNamespace((Owner) null);
        Content content4 = TestUtil.createContent("test_content-4", "test_content-4")
            .setNamespace(owner1.getKey());
        Content content5 = TestUtil.createContent("test_content-5", "test_content-5")
            .setNamespace(owner2.getKey());

        this.createContent(content1);
        this.createContent(content2);
        this.createContent(content3);
        this.createContent(content4);
        this.createContent(content5);

        List<String> ids = Stream.of(content3, content4, content5)
            .map(Content::getId)
            .toList();

        Stream<ContentDTO> response = this.ownerContentResource
            .getContentsByOwner(owner1.getKey(), ids, false);

        List<ContentDTO> expected = Stream.of(content3, content4)
            .map(this.modelTranslator.getStreamMapper(Content.class, ContentDTO.class))
            .toList();

        assertNotNull(response);
        assertThat(response.toList())
            .hasSize(expected.size())
            .containsAll(expected);
    }

    @Test
    public void testGetContentsByOwnerWithEntityIDFilteringAndOmitGlobalsSet() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Content content1 = TestUtil.createContent("test_content-1", "test_content-1")
            .setNamespace((Owner) null);
        Content content2 = TestUtil.createContent("test_content-2", "test_content-2")
            .setNamespace(owner1.getKey());
        Content content3 = TestUtil.createContent("test_content-3", "test_content-3")
            .setNamespace((Owner) null);
        Content content4 = TestUtil.createContent("test_content-4", "test_content-4")
            .setNamespace(owner1.getKey());
        Content content5 = TestUtil.createContent("test_content-5", "test_content-5")
            .setNamespace(owner2.getKey());

        this.createContent(content1);
        this.createContent(content2);
        this.createContent(content3);
        this.createContent(content4);
        this.createContent(content5);

        List<String> ids = Stream.of(content3, content4, content5)
            .map(Content::getId)
            .toList();

        Stream<ContentDTO> response = this.ownerContentResource
            .getContentsByOwner(owner1.getKey(), ids, true);

        List<ContentDTO> expected = Stream.of(content4)
            .map(this.modelTranslator.getStreamMapper(Content.class, ContentDTO.class))
            .toList();

        assertNotNull(response);
        assertThat(response.toList())
            .hasSize(expected.size())
            .containsAll(expected);
    }

    @Test
    public void testGetContentsByOwnerWithNoContent() throws Exception {
        Owner owner = this.createOwner("test_owner");

        Stream<ContentDTO> response = this.ownerContentResource
            .getContentsByOwner(owner.getKey(), List.of(), false);
        assertNotNull(response);

        List<ContentDTO> received = response.toList();
        assertEquals(0, received.size());
    }

    @Test
    public void testGetContentsByOwnerBadOwner() throws Exception {
        Owner owner = this.createOwner("test_owner");

        assertThrows(NotFoundException.class,
            () -> this.ownerContentResource.getContentsByOwner("bad owner", List.of(), false));
    }

    @Test
    public void testGetContentById() {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content");
        ContentDTO output = this.ownerContentResource.getContentById(owner.getKey(), content.getId());

        assertNotNull(output);
        assertEquals(content.getId(), output.getId());
    }

    @Test
    public void testGetContentByIdNotFound() {
        Owner owner = this.createOwner("test_owner");

        assertThrows(NotFoundException.class,
            () -> this.ownerContentResource.getContentById(owner.getKey(), "test_content"));
    }

    @Test
    public void testCreateContent() {
        Owner owner = this.createOwner("test_owner");
        ContentDTO cdto = TestUtil.createContentDTO("test_content");
        cdto.setLabel("test-label");
        cdto.setType("test-test");
        cdto.setVendor("test-vendor");

        assertNull(this.contentCurator.getContentById(owner.getKey(), cdto.getId()));

        ContentDTO output = this.ownerContentResource.createContent(owner.getKey(), cdto);

        assertNotNull(output);
        assertEquals(cdto.getId(), output.getId());

        Content entity = this.contentCurator.getContentById(owner.getKey(), cdto.getId());
        assertNotNull(entity);
        assertEquals(cdto.getName(), entity.getName());
        assertEquals(cdto.getLabel(), entity.getLabel());
        assertEquals(cdto.getType(), entity.getType());
        assertEquals(cdto.getVendor(), entity.getVendor());
    }

    @Test
    public void createContentWhenContentAlreadyExistsInNamespace() {
        Owner owner = this.createOwner("test_owner");
        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace(owner.getKey());
        content = this.contentCurator.create(content);

        ContentDTO update = TestUtil.createContentDTO("test_content", "updated_name");
        update.setLabel("test-label");
        update.setType("test-test");
        update.setVendor("test-vendor");

        assertNotNull(this.contentCurator.getContentById(owner.getKey(), update.getId()));

        ContentDTO output = this.ownerContentResource.createContent(owner.getKey(), update);

        assertNotNull(output);
        assertEquals(update.getId(), output.getId());
        assertEquals(update.getName(), output.getName());

        Content entity = this.contentCurator.getContentById(owner.getKey(), update.getId());
        assertNotNull(entity);
        assertEquals(update.getName(), entity.getName());
    }

    @Test
    public void testCreateContentWhenContentAlreadyExistsInGlobalNamespace() {
        Owner owner = this.createOwner("test_owner");
        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace((Owner) null);
        content = this.contentCurator.create(content);

        ContentDTO update = TestUtil.createContentDTO("test_content", "updated_name");
        update.setLabel("test-label");
        update.setType("test-test");
        update.setVendor("test-vendor");

        assertNotNull(this.contentCurator.getContentById(null, update.getId()));
        assertNull(this.contentCurator.getContentById(owner.getKey(), update.getId()));

        assertThrows(BadRequestException.class,
            () -> this.ownerContentResource.createContent(owner.getKey(), update));
    }

    @Test
    public void testCreateContentInOrgUsingLongKey() {
        Owner owner = this.createOwner("test_owner".repeat(25));
        ContentDTO cdto = TestUtil.createContentDTO("test_content");
        cdto.setLabel("test-label");
        cdto.setType("test-test");
        cdto.setVendor("test-vendor");

        assertNull(this.contentCurator.getContentById(owner.getKey(), cdto.getId()));

        ContentDTO output = this.ownerContentResource.createContent(owner.getKey(), cdto);

        assertNotNull(output);
        assertEquals(cdto.getId(), output.getId());

        Content entity = this.contentCurator.getContentById(owner.getKey(), cdto.getId());
        assertNotNull(entity);
        assertEquals(cdto.getName(), entity.getName());
        assertEquals(cdto.getLabel(), entity.getLabel());
        assertEquals(cdto.getType(), entity.getType());
        assertEquals(cdto.getVendor(), entity.getVendor());
    }

    @Test
    public void testUpdateContent() {
        Owner owner = this.createOwner("test_owner");
        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace(owner.getKey());
        content = this.contentCurator.create(content);

        ContentDTO update = TestUtil.createContentDTO("test_content", "updated_name");
        assertNotNull(this.contentCurator.getContentById(owner.getKey(), update.getId()));

        ContentDTO output = this.ownerContentResource.updateContent(owner.getKey(), update.getId(), update);

        assertNotNull(output);
        assertEquals(update.getId(), output.getId());
        assertEquals(update.getName(), output.getName());

        Content entity = this.contentCurator.getContentById(owner.getKey(), update.getId());

        assertNotNull(entity);
        assertEquals(update.getName(), entity.getName());
    }

    @Test
    public void testUpdateContentThatDoesntExist() {
        Owner owner = this.createOwner("test_owner");
        ContentDTO update = TestUtil.createContentDTO("test_content", "updated_name");

        assertNull(this.contentCurator.resolveContentId(owner.getKey(), update.getId()));

        assertThrows(NotFoundException.class,
            () -> this.ownerContentResource.updateContent(owner.getKey(), update.getId(), update));

        assertNull(this.contentCurator.resolveContentId(owner.getKey(), update.getId()));
    }

    @Test
    public void testCannotUpdateContentInGlobalNamespace() {
        Owner owner = this.createOwner("test_owner");

        Content template = TestUtil.createContent("test_content", "test_content")
            .setNamespace((Owner) null);
        Content content = this.contentCurator.create(template);

        ContentDTO update = TestUtil.createContentDTO(content.getId(), "updated_name");

        assertThrows(ForbiddenException.class,
            () -> this.ownerContentResource.updateContent(owner.getKey(), update.getId(), update));
    }

    @Test
    public void testCannotUpdateContentInOtherNamespace() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Content template = TestUtil.createContent("test_content", "test_content")
            .setNamespace(owner2.getKey());
        Content content = this.contentCurator.create(template);

        ContentDTO update = TestUtil.createContentDTO(content.getId(), "updated_name");

        assertThrows(NotFoundException.class,
            () -> this.ownerContentResource.updateContent(owner1.getKey(), update.getId(), update));
    }

    @Test
    public void testRemoveContent() {
        Owner owner = this.createOwner("test_owner");
        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace(owner.getKey());
        content = this.contentCurator.create(content);

        assertNotNull(this.contentCurator.getContentById(owner.getKey(), content.getId()));

        this.ownerContentResource.removeContent(owner.getKey(), content.getId());

        assertNull(this.contentCurator.getContentById(owner.getKey(), content.getId()));
        assertNull(this.contentCurator.get(content.getUuid()));
    }

    @Test
    public void testCannotRemoveContentFromGlobalNamespace() {
        Owner owner = this.createOwner("test_owner");

        Content template = TestUtil.createContent("test_content", "test_content")
            .setNamespace((Owner) null);
        Content content = this.contentCurator.create(template);

        assertThrows(ForbiddenException.class,
            () -> this.ownerContentResource.removeContent(owner.getKey(), content.getId()));
    }

    @Test
    public void testCannotRemoveContentFromOtherNamespace() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Content template = TestUtil.createContent("test_content", "test_content")
            .setNamespace(owner2.getKey());
        Content content = this.contentCurator.create(template);

        assertThrows(NotFoundException.class,
            () -> this.ownerContentResource.removeContent(owner1.getKey(), content.getId()));
    }

    @Test
    public void deleteContentWithNonExistentContent() {
        Owner owner = this.createOwner("test_owner");

        assertThrows(NotFoundException.class,
            () -> this.ownerContentResource.removeContent(owner.getKey(), "test_content"));
    }

    @Test
    public void testUpdateContentThrowsExceptionWhenOwnerDoesNotExist() {
        ContentDTO cdto = TestUtil.createContentDTO("test_content");

        assertThrows(NotFoundException.class,
            () -> this.ownerContentResource.updateContent("fake_owner_key", cdto.getId(), cdto));
    }
}
