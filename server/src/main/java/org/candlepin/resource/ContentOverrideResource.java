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
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.interceptor.SecurityHole;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.ContentOverride;
import org.candlepin.model.ContentOverrideCurator;
import org.candlepin.util.ContentOverrideValidator;
import org.xnap.commons.i18n.I18n;

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
    private I18n i18n;

    public ContentOverrideResource(Curator contentOverrideCurator,
            ContentOverrideValidator contentOverrideValidator,
            I18n i18n, String parentPath) {
        this.contentOverrideCurator = contentOverrideCurator;
        this.contentOverrideValidator = contentOverrideValidator;
        this.parentPath = parentPath;
        this.i18n = i18n;
    }

    protected abstract Parent findParentById(String parentId);

    protected String getParentPath() {
        return parentPath;
    }

    /**
     * Adds a Content Override to a Principal
     *
     * @param info context to get the parent id
     * @param entries overrides to add or update
     *
     * @return a list of ContentOverride objects
     * @httpcode 404
     * @httpcode 200
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    @SecurityHole
    public List<T> addContentOverrides(
        @Context UriInfo info,
        @Context Principal principal,
        List<ContentOverride> entries) {
        String parentId = info.getPathParameters().getFirst(this.getParentPath());

        Parent parent = this.verifyAndGetParent(parentId, principal, Access.ALL);
        contentOverrideValidator.validate(entries);
        for (ContentOverride entry : entries) {
            contentOverrideCurator.addOrUpdate(parent, entry);
        }
        return contentOverrideCurator.getList(parent);
    }

    /**
     * Removes a Content Override from a Principal
     *
     * @param info context to get the parent id
     * @param entries overrides to remove to remove
     *
     * @return a list of ContentOverride objects
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    @SecurityHole
    public List<T> deleteContentOverrides(
        @Context UriInfo info,
        @Context Principal principal,
        List<ContentOverride> entries) {

        String parentId = info.getPathParameters().getFirst(this.getParentPath());

        Parent parent = this.verifyAndGetParent(parentId, principal, Access.ALL);
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
     * Retrieves list of Content Overrides
     * <p>
     * Based on the Consumer or Activation Key
     *
     * @param info context to get the parent id
     *
     * @return a list of ContentOverride objects
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityHole
    public List<T> getContentOverrideList(
        @Context UriInfo info,
        @Context Principal principal) {
        String parentId = info.getPathParameters().getFirst(this.getParentPath());
        Parent parent = this.verifyAndGetParent(parentId, principal, Access.READ_ONLY);
        return contentOverrideCurator.getList(parent);
    }

    private Parent verifyAndGetParent(String parentId, Principal principal, Access access) {
        // Throws exception if criteria block the id
        Parent result = this.findParentById(parentId);
        // Now that we know it exists, verify access level
        if (!principal.canAccess(result, SubResource.NONE, access)) {
            String error = "Insufficient permissions";
            throw new ForbiddenException(i18n.tr(error));
        }
        return result;
    }
}
