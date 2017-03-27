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
import static org.junit.Assert.*;

import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.controller.ContentManager;
import org.candlepin.controller.OwnerManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.jackson.ProductCachedSerializationModule;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Content;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.model.dto.ContentData;
import org.candlepin.service.impl.DefaultUniqueIdGenerator;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;

import javax.inject.Inject;



/**
 * OwnerContentResourceTest
 */
public class OwnerContentResourceTest extends DatabaseTestFixture {

    @Inject protected ContentManager contentManager;
    @Inject protected PoolManager poolManager;
    @Inject protected OwnerManager ownerManager;

    private OwnerContentResource ownerContentResource;

    @Before
    public void setup() {
        this.ownerContentResource = new OwnerContentResource(this.contentCurator, this.contentManager,
        this.environmentContentCurator, this.i18n, this.ownerCurator, this.ownerContentCurator,
        this.poolManager, this.productCurator, new DefaultUniqueIdGenerator(),
        new ProductCachedSerializationModule(productCurator), ownerManager);
    }

    @Test
    public void listContent() throws Exception {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);

        CandlepinQuery<Content> response = this.ownerContentResource.listContent(owner.getKey());

        assertNotNull(response);

        Collection<Content> received = response.list();

        assertEquals(1, received.size());
        assertTrue(received.contains(content));
    }

    @Test
    public void listContentNoContent() throws Exception {
        Owner owner = this.createOwner("test_owner");

        CandlepinQuery<Content> response = this.ownerContentResource.listContent(owner.getKey());

        assertNotNull(response);

        Collection<Content> received = response.list();

        assertEquals(0, received.size());
    }

    @Test
    public void getContent() {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);

        ContentData output = this.ownerContentResource.getContent(owner.getKey(), content.getId());

        assertNotNull(output);
        assertEquals(content.getId(), output.getId());
        assertFalse(content.isChangedBy(output));
    }

    @Test(expected = NotFoundException.class)
    public void getContentNotFound() {
        Owner owner = this.createOwner("test_owner");

        ContentData output = this.ownerContentResource.getContent(owner.getKey(), "test_content");
    }

    @Test
    public void createContent() {
        Owner owner = this.createOwner("test_owner");
        ContentData contentData = TestUtil.createContentDTO("test_content");
        contentData.setLabel("test-label");
        contentData.setType("test-test");
        contentData.setVendor("test-vendor");

        assertNull(this.ownerContentCurator.getContentById(owner, contentData.getId()));

        ContentData output = this.ownerContentResource.createContent(owner.getKey(), contentData);

        assertNotNull(output);
        assertEquals(contentData.getId(), output.getId());

        Content entity = this.ownerContentCurator.getContentById(owner, contentData.getId());
        assertNotNull(entity);
        assertFalse(entity.isChangedBy(contentData));
    }

    @Test
    public void createContentWhenContentAlreadyExists()  {
        // Note:
        // The current behavior of createContent is to update content if content already exists
        // with the given RHID. So, our expected behavior in this test is to trigger an update.

        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        ContentData contentData = TestUtil.createContentDTO("test_content", "updated_name");
        contentData.setLabel("test-label");
        contentData.setType("test-test");
        contentData.setVendor("test-vendor");

        assertNotNull(this.ownerContentCurator.getContentById(owner, contentData.getId()));

        ContentData output = this.ownerContentResource.createContent(owner.getKey(), contentData);

        assertNotNull(output);
        assertEquals(contentData.getId(), output.getId());
        assertEquals(contentData.getName(), output.getName());

        Content entity = this.ownerContentCurator.getContentById(owner, contentData.getId());
        assertNotNull(entity);
        assertFalse(entity.isChangedBy(contentData));
    }

    @Test(expected = ForbiddenException.class)
    public void createContentWhenContentAlreadyExistsAndLocked()  {
        // Note:
        // The current behavior of createContent is to update content if content already exists
        // with the given RHID. So, our expected behavior in this test is to trigger an update.

        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        ContentData contentData = TestUtil.createContentDTO("test_content", "updated_name");
        contentData.setLabel("test-label");
        contentData.setType("test-test");
        contentData.setVendor("test-vendor");

        content.setLocked(true);
        this.contentCurator.merge(content);

        assertNotNull(this.ownerContentCurator.getContentById(owner, contentData.getId()));

        try {
            ContentData output = this.ownerContentResource.createContent(owner.getKey(), contentData);
        }
        catch (ForbiddenException e) {
            Content entity = this.ownerContentCurator.getContentById(owner, contentData.getId());
            assertNotNull(entity);
            assertEquals(content, entity);
            assertTrue(entity.isChangedBy(contentData));

            throw e;
        }
    }

    @Test
    public void updateContent()  {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        ContentData contentData = TestUtil.createContentDTO("test_content", "updated_name");

        assertNotNull(this.ownerContentCurator.getContentById(owner, contentData.getId()));

        ContentData output = this.ownerContentResource.updateContent(owner.getKey(), contentData.getId(),
            contentData);

        assertNotNull(output);
        assertEquals(contentData.getId(), output.getId());
        assertEquals(contentData.getName(), output.getName());

        Content entity = this.ownerContentCurator.getContentById(owner, contentData.getId());
        assertNotNull(entity);
        assertFalse(entity.isChangedBy(contentData));
    }

    @Test(expected = NotFoundException.class)
    public void updateContentThatDoesntExist()  {
        Owner owner = this.createOwner("test_owner");
        ContentData contentData = TestUtil.createContentDTO("test_content", "updated_name");

        assertNull(this.ownerContentCurator.getContentById(owner, contentData.getId()));

        try {
            this.ownerContentResource.updateContent(owner.getKey(), contentData.getId(), contentData);
        }
        catch (NotFoundException e) {
            assertNull(this.ownerContentCurator.getContentById(owner, contentData.getId()));

            throw e;
        }
    }

    @Test(expected = ForbiddenException.class)
    public void updateLockedContent()  {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        ContentData contentData = TestUtil.createContentDTO("test_content", "updated_name");
        content.setLocked(true);
        this.contentCurator.merge(content);

        assertNotNull(this.ownerContentCurator.getContentById(owner, contentData.getId()));

        try {
            this.ownerContentResource.updateContent(owner.getKey(), contentData.getId(), contentData);
        }
        catch (ForbiddenException e) {
            Content entity = this.ownerContentCurator.getContentById(owner, contentData.getId());
            assertNotNull(entity);
            assertTrue(entity.isChangedBy(contentData));

            throw e;
        }
    }

    @Test
    public void deleteContent() {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        Environment environment = this.createEnvironment(owner, "test_env", "test_env", null, null,
            Arrays.asList(content));

        assertNotNull(this.ownerContentCurator.getContentById(owner, content.getId()));

        this.ownerContentResource.remove(owner.getKey(), content.getId());

        assertNull(this.ownerContentCurator.getContentById(owner, content.getId()));

        this.environmentCurator.evict(environment);
        environment = this.environmentCurator.find(environment.getId());

        assertEquals(0, environment.getEnvironmentContent().size());
    }

    @Test(expected = ForbiddenException.class)
    public void deleteLockedContent() {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        content.setLocked(true);
        this.contentCurator.merge(content);

        Environment environment = this.createEnvironment(owner, "test_env", "test_env", null, null,
            Arrays.asList(content));

        assertNotNull(this.ownerContentCurator.getContentById(owner, content.getId()));

        try {
            this.ownerContentResource.remove(owner.getKey(), content.getId());
        }
        catch (ForbiddenException e) {
            assertNotNull(this.ownerContentCurator.getContentById(owner, content.getId()));

            this.environmentCurator.evict(environment);
            environment = this.environmentCurator.find(environment.getId());

            assertEquals(1, environment.getEnvironmentContent().size());

            throw e;
        }
    }

    @Test(expected = NotFoundException.class)
    public void deleteContentWithNonExistentContent() {
        Owner owner = this.createOwner("test_owner");

        this.ownerContentResource.remove(owner.getKey(), "test_content");
    }

    @Test(expected = NotFoundException.class)
    public void testUpdateContentThrowsExceptionWhenOwnerDoesNotExist() {
        ContentData contentData = TestUtil.createContentDTO("test_content");

        this.ownerContentResource.updateContent("fake_owner_key", contentData.getId(), contentData);
    }

}
