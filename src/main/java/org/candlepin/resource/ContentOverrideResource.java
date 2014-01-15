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

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang.StringUtils;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.ContentOverride;
import org.candlepin.model.ContentOverrideCurator;
import org.candlepin.util.ContentOverrideValidator;

import com.google.inject.persist.Transactional;

/**
 * Abstraction of the API Gateway for Content Overrides
 *
 * @param <T> ContentOverride type
 * @param <Curator> curator class for the ContentOverride type
 * @param <Parent> parent of the content override, Consumer or ActivationKey for example
 */
public abstract class ContentOverrideResource<T extends ContentOverride,
        Curator extends ContentOverrideCurator<T, Parent>,
        Parent extends AbstractHibernateObject> {

    private Curator contentOverrideCurator;
    private ContentOverrideValidator contentOverrideValidator;
    private String parentPath;

    public ContentOverrideResource(Curator contentOverrideCurator,
            ContentOverrideValidator contentOverrideValidator,
            String parentPath) {
        this.contentOverrideCurator = contentOverrideCurator;
        this.contentOverrideValidator = contentOverrideValidator;
        this.parentPath = parentPath;
    }

    protected abstract Parent findParentById(String parentId);

    protected String getParentPath() {
        return parentPath;
    }

    /**
     * Add override for content set
     *
     * @param uuid
     *
     * @return list of active overrides
     * @httpcode 404
     * @httpcode 200
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public List<T> addContentOverrides(
        @Context UriInfo info,
        List<ContentOverride> entries) {
        String parentId = info.getPathParameters().getFirst(this.getParentPath());

        Parent parent = this.findParentById(parentId);
        contentOverrideValidator.validate(entries);
        for (ContentOverride entry : entries) {
            contentOverrideCurator.addOrUpdate(parent, entry);
        }
        return contentOverrideCurator.getList(parent);
    }

    /**
     * Remove override based on included criteria
     *
     * @param uuid
     *
     * @return list of active overrides
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public List<T> deleteContentOverrides(
        @Context UriInfo info,
        List<ContentOverride> entries) {

        String parentId = info.getPathParameters().getFirst(this.getParentPath());

        Parent parent = this.findParentById(parentId);
        if (entries.size() == 0) {
            contentOverrideCurator.removeByParent(parent);
        }
        else {
            for (ContentOverride entry : entries) {
                String label = entry.getContentLabel();
                if (StringUtils.isBlank(label)) {
                    contentOverrideCurator.removeByParent(parent);
                }
                else {
                    String name = entry.getName();
                    if (StringUtils.isBlank(name)) {
                        contentOverrideCurator.removeByContentLabel(
                            parent, entry.getContentLabel());
                    }
                    else {
                        contentOverrideCurator.removeByName(parent,
                            entry.getContentLabel(), name);
                    }
                }
            }
        }
        return contentOverrideCurator.getList(parent);
    }

    /**
     * Get the list of content set overrides for this consumer
     *
     * @param uuid
     *
     * @return list of active overrides
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<T> getContentOverrideList(
        @Context UriInfo info) {
        String parentId = info.getPathParameters().getFirst(this.getParentPath());
        Parent parent = this.findParentById(parentId);
        return contentOverrideCurator.getList(parent);
    }
}
