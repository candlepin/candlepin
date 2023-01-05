/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.spec.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

@SpecTest
public class ContentVersioningSpecTest {
    @Test
    public void shouldCreateOneContentInstanceWhenSharedByMultipleOrgs() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        ContentDTO expectedContent = Contents.random();

        ContentDTO owner1Content = adminClient.ownerContent().createContent(owner.getKey(), expectedContent);
        ContentDTO owner2Content = adminClient.ownerContent().createContent(owner2.getKey(), expectedContent);

        assertThat(owner1Content)
            .returns(owner2Content.getUuid(), ContentDTO::getUuid)
            .isEqualTo(owner2Content)
            .returns(expectedContent.getId(), ContentDTO::getId)
            .returns(expectedContent.getName(), ContentDTO::getName)
            .returns(expectedContent.getLabel(), ContentDTO::getLabel)
            .returns(expectedContent.getVendor(), ContentDTO::getVendor)
            .returns(expectedContent.getContentUrl(), ContentDTO::getContentUrl);
    }

    @Test
    public void shouldCreateTwoDistinctContentInstancesWhenDetailsDiffer() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        ContentDTO expectedContent1 = Contents.random();
        ContentDTO expectedContent2 = new ContentDTO()
            .id(expectedContent1.getId())
            .name(expectedContent1.getName() + "-2")
            .label(expectedContent1.getLabel())
            .type(expectedContent1.getType())
            .vendor(expectedContent1.getVendor())
            .contentUrl(expectedContent1.getContentUrl());

        ContentDTO owner1Content = adminClient.ownerContent()
            .createContent(owner.getKey(), expectedContent1);
        ContentDTO owner2Content = adminClient.ownerContent()
            .createContent(owner2.getKey(), expectedContent2);

        assertThat(owner1Content.getUuid()).isNotNull();
        assertThat(owner2Content.getUuid()).isNotNull();
        assertNotEquals(owner1Content.getUuid(), owner2Content.getUuid());
    }

    @Test
    public void shouldCreateANewContentInstanceWhenAnOrgUpdatesASharedInstance() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        OwnerDTO owner3 = adminClient.owners().createOwner(Owners.random());
        ContentDTO expectedContent = Contents.random();

        ContentDTO owner1Content = adminClient.ownerContent().createContent(owner.getKey(), expectedContent);
        ContentDTO owner2Content = adminClient.ownerContent().createContent(owner2.getKey(), expectedContent);
        ContentDTO owner3Content = adminClient.ownerContent().createContent(owner3.getKey(), expectedContent);

        assertThat(owner1Content.getUuid())
            .isEqualTo(owner2Content.getUuid())
            .isEqualTo(owner3Content.getUuid());

        expectedContent.name(StringUtil.random("updated-name-"));
        ContentDTO owner2UpdatedContent = adminClient.ownerContent()
            .createContent(owner2.getKey(), expectedContent);

        assertThat(adminClient.ownerContent().listOwnerContent(owner2.getKey()))
            .singleElement()
            .returns(owner2UpdatedContent.getUuid(), ContentDTO::getUuid)
            .doesNotReturn(owner2Content.getUuid(), ContentDTO::getUuid);

        assertThat(adminClient.ownerContent().listOwnerContent(owner.getKey()))
            .singleElement()
            .returns(owner1Content.getUuid(), ContentDTO::getUuid);

        assertThat(adminClient.ownerContent().listOwnerContent(owner3.getKey()))
            .singleElement()
            .returns(owner3Content.getUuid(), ContentDTO::getUuid);
    }

    @Test
    public void shouldCreateANewInstanceWhenMakingChangesToAnExistingInstance() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ContentDTO content = adminClient.ownerContent().createContent(owner.getKey(), Contents.random());

        content.setName(content.getName() + "-2");
        ContentDTO updatedContent = adminClient.ownerContent()
            .updateContent(owner.getKey(), content.getId(), content);

        assertThat(updatedContent)
            .isNotNull()
            .doesNotReturn(content.getUuid(), ContentDTO::getUuid);

        // content should now be different from both content1 and 2, and content2 should no longer exist
        assertThat(adminClient.ownerContent().listOwnerContent(owner.getKey()))
            .singleElement()
            .returns(updatedContent.getUuid(), ContentDTO::getUuid)
            .doesNotReturn(content.getUuid(), ContentDTO::getUuid);
    }

    @Test
    public void shouldConvergeContentWhenAGivenVersionAlreadyExists() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        OwnerDTO owner3 = adminClient.owners().createOwner(Owners.random());

        ContentDTO expectedContent = Contents.random();
        ContentDTO owner1Content = adminClient.ownerContent().createContent(owner.getKey(), expectedContent);
        ContentDTO owner2Content = adminClient.ownerContent().createContent(owner2.getKey(), expectedContent);
        String contentOriginalName = expectedContent.getName();
        expectedContent.setName(contentOriginalName + "-2");
        ContentDTO owner3Content = adminClient.ownerContent().createContent(owner3.getKey(), expectedContent);

        assertThat(owner1Content.getUuid())
            .isEqualTo(owner2Content.getUuid())
            .isNotEqualTo(owner3Content.getUuid());

        expectedContent.setName(contentOriginalName);
        ContentDTO owner3UpdatedContent = adminClient.ownerContent()
            .updateContent(owner3.getKey(), expectedContent.getId(), expectedContent);

        assertThat(owner3UpdatedContent)
            .isNotNull()
            .returns(owner1Content.getUuid(), ContentDTO::getUuid);

        assertThat(adminClient.ownerContent().listOwnerContent(owner.getKey()))
            .singleElement()
            .returns(owner1Content.getUuid(), ContentDTO::getUuid);

        assertThat(adminClient.ownerContent().listOwnerContent(owner2.getKey()))
            .singleElement()
            .returns(owner2Content.getUuid(), ContentDTO::getUuid);

        assertThat(adminClient.ownerContent().listOwnerContent(owner3.getKey()))
            .singleElement()
            .returns(owner1Content.getUuid(), ContentDTO::getUuid)
            .doesNotReturn(owner3Content.getUuid(), ContentDTO::getUuid);
    }

    @Test
    public void shouldDivergeWhenUpdatingSharedContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());

        ContentDTO expectedContent = Contents.random();
        ContentDTO owner1Content = adminClient.ownerContent().createContent(owner.getKey(), expectedContent);
        ContentDTO owner2Content = adminClient.ownerContent().createContent(owner2.getKey(), expectedContent);

        assertThat(owner1Content).isNotNull();
        assertThat(owner2Content)
            .isNotNull()
            .returns(owner1Content.getUuid(), ContentDTO::getUuid);

        expectedContent.setName(expectedContent.getName() + "-2");
        ContentDTO owner2UpdatedContent = adminClient.ownerContent()
            .createContent(owner2.getKey(), expectedContent);
        assertThat(owner2UpdatedContent)
            .isNotNull()
            .doesNotReturn(owner2Content.getUuid(), ContentDTO::getUuid);

        assertThat(adminClient.ownerContent().listOwnerContent(owner.getKey()))
            .singleElement()
            .returns(owner1Content.getUuid(), ContentDTO::getUuid)
            .returns(owner2Content.getUuid(), ContentDTO::getUuid)
            .doesNotReturn(owner2UpdatedContent.getUuid(), ContentDTO::getUuid);

        assertThat(adminClient.ownerContent().listOwnerContent(owner2.getKey()))
            .singleElement()
            .doesNotReturn(owner1Content.getUuid(), ContentDTO::getUuid)
            .doesNotReturn(owner2Content.getUuid(), ContentDTO::getUuid)
            .returns(owner2UpdatedContent.getUuid(), ContentDTO::getUuid);
    }

    @Test
    public void shouldDeleteContentWithoutAffectingOtherOrgs() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());

        ContentDTO expectedContent = Contents.random();
        ContentDTO owner1Content = adminClient.ownerContent().createContent(owner.getKey(), expectedContent);
        ContentDTO owner2Content = adminClient.ownerContent().createContent(owner2.getKey(), expectedContent);

        assertThat(owner1Content).isNotNull();
        assertThat(owner2Content)
            .isNotNull()
            .returns(owner1Content.getUuid(), ContentDTO::getUuid);

        adminClient.ownerContent().remove(owner.getKey(), expectedContent.getId());

        assertThat(adminClient.ownerContent().listOwnerContent(owner.getKey()))
            .isEmpty();

        assertNotFound(() -> adminClient.ownerContent()
            .getOwnerContent(owner.getKey(), expectedContent.getId()));

        assertThat(adminClient.ownerContent().listOwnerContent(owner2.getKey()))
            .singleElement()
            .returns(owner2Content.getUuid(), ContentDTO::getUuid);
    }

    @Test
    public void shouldCleanupOrphansWithoutInterferingWithNormalActions() throws Exception {
        // This test takes advantage of the immutable nature of contents with the in-place update branch
        // disabled. If in-place updates are ever reenabled, we'll need a way to generate large numbers
        // of orphaned contents for this test.
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        String contentIdPrefix = "content-id-";
        int offset = 1000;
        int numberOfContent = 100;
        for (int i = 0; i < 10; i++) {
            // Create a bunch of dummy contents
            final int batchOffset = offset;
            Set<String> owner1CreatedContentUuids = new HashSet<>();
            Set<String> owner2CreatedContentUuids = new HashSet<>();
            for (int j = batchOffset; j < batchOffset + numberOfContent; j++) {
                String id = contentIdPrefix + j;
                adminClient.ownerContent().createContent(owner.getKey(), Contents.random().id(id));
            }

            // Attempt to update and create new contents to get into some funky race conditions with
            // convergence and orphanization
            Thread owner1UpdatesThread = new Thread(() -> {
                for (int j = batchOffset; j < batchOffset + numberOfContent; j++) {
                    try {
                        String id = contentIdPrefix + j;
                        ContentDTO createdContent = adminClient.ownerContent()
                            .updateContent(owner.getKey(), id, Contents.random().id(id));
                        owner1CreatedContentUuids.add(createdContent.getUuid());
                    }
                    catch (Exception e) {
                        // Swollow errors
                    }
                }
            });

            Thread owner2GenerationThread = new Thread(() -> {
                for (int j = batchOffset; j < batchOffset + numberOfContent; j++) {
                    String id = contentIdPrefix + j;
                    ContentDTO createdContent = adminClient.ownerContent()
                        .createContent(owner2.getKey(), Contents.random().id(id));
                    owner2CreatedContentUuids.add(createdContent.getUuid());
                }
            });

            owner1UpdatesThread.start();
            owner2GenerationThread.start();

            AsyncJobStatusDTO job = adminClient.jobs().scheduleJob("OrphanCleanupJob");
            job = adminClient.jobs().waitForJob(job);
            assertEquals("FINISHED", job.getState());

            // Wait for the threads to finish
            owner1UpdatesThread.join(90000);
            owner2GenerationThread.join(90000);

            // Verify the contents created/updated still exist
            for (String uuid : owner1CreatedContentUuids) {
                ContentDTO owner1Content = adminClient.content().getContent(uuid);
                assertThat(owner1Content)
                    .isNotNull()
                    .returns(uuid, ContentDTO::getUuid);
            }

            for (String uuid : owner2CreatedContentUuids) {
                ContentDTO owner2Content = adminClient.content().getContent(uuid);
                assertThat(owner2Content)
                    .isNotNull()
                    .returns(uuid, ContentDTO::getUuid);
            }

            offset += numberOfContent;
        }
    }

}
