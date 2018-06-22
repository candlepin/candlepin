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

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.ContentOverrideDTO;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.ContentOverride;
import org.candlepin.model.ContentOverrideCurator;
import org.candlepin.util.ContentOverrideValidator;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang.StringUtils;
import org.xnap.commons.i18n.I18n;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;



/**
 * Abstraction of the API Gateway for Content Overrides
 *
 * @param <T> ContentOverride type
 * @param <Curator> curator class for the ContentOverride type
 * @param <Parent> parent of the content override, Consumer or ActivationKey for example
 */
public abstract class ContentOverrideResource<T extends ContentOverride<T, Parent>,
    Curator extends ContentOverrideCurator<T, Parent>,
    Parent extends AbstractHibernateObject> {

    protected I18n i18n;
    protected Curator curator;
    protected ModelTranslator translator;
    protected ContentOverrideValidator validator;

    public ContentOverrideResource(I18n i18n, Curator curator, ModelTranslator translator,
        ContentOverrideValidator validator) {

        this.i18n = i18n;
        this.curator = curator;
        this.translator = translator;
        this.validator = validator;
    }

    protected abstract Parent findParentById(String parentId);

    protected abstract String getParentPath();

    /**
     * Creates an empty/default override, to be completed by the caller.
     *
     * @return
     *  An empty/default ContentOverride instance
     */
    protected abstract T createOverride();


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
    public CandlepinQuery<ContentOverrideDTO> addContentOverrides(
        @Context UriInfo info,
        @Context Principal principal,
        List<ContentOverrideDTO> entries) {

        // Validate our input
        this.validator.validate(entries);

        // Fetch the "parent" content override object...
        String parentId = info.getPathParameters().getFirst(this.getParentPath());
        Parent parent = this.verifyAndGetParent(parentId, principal, Access.ALL);

        try {
            for (ContentOverrideDTO dto : entries) {
                T override = this.curator.retrieve(parent, dto.getContentLabel(), dto.getName());

                // We're counting on Hibernate to do our batching for us here...
                if (override != null) {
                    override.setValue(dto.getValue());
                    this.curator.merge(override);
                }
                else {
                    override = this.createOverride();

                    override.setParent(parent);
                    override.setContentLabel(dto.getContentLabel());
                    override.setName(dto.getName());
                    override.setValue(dto.getValue());

                    this.curator.create(override);
                }
            }
        }
        catch (RuntimeException e) {
            // Make sure we clear all pending changes, since we don't want to risk storing only a
            // portion of the changes.
            this.curator.clear();

            // Re-throw the exception
            throw e;
        }

        // Hibernate typically persists automatically before executing a query against a table with
        // pending changes, but if it doesn't, we can add a flush here to make sure this outputs the
        // correct values
        CandlepinQuery<T> query = this.curator.getList(parent);
        return this.translator.translateQuery(query, ContentOverrideDTO.class);
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
    public CandlepinQuery<ContentOverrideDTO> deleteContentOverrides(
        @Context UriInfo info,
        @Context Principal principal,
        List<ContentOverrideDTO> entries) {

        String parentId = info.getPathParameters().getFirst(this.getParentPath());
        Parent parent = this.verifyAndGetParent(parentId, principal, Access.ALL);

        if (entries.size() == 0) {
            this.curator.removeByParent(parent);
        }
        else {
            for (ContentOverrideDTO dto : entries) {
                String label = dto.getContentLabel();
                if (StringUtils.isBlank(label)) {
                    this.curator.removeByParent(parent);
                }
                else {
                    String name = dto.getName();
                    if (StringUtils.isBlank(name)) {
                        this.curator.removeByContentLabel(parent, dto.getContentLabel());
                    }
                    else {
                        this.curator.removeByName(parent, dto.getContentLabel(), name);
                    }
                }
            }
        }

        CandlepinQuery<T> query = this.curator.getList(parent);
        return this.translator.translateQuery(query, ContentOverrideDTO.class);
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
    public CandlepinQuery<ContentOverrideDTO> getContentOverrideList(
        @Context UriInfo info,
        @Context Principal principal) {

        String parentId = info.getPathParameters().getFirst(this.getParentPath());
        Parent parent = this.verifyAndGetParent(parentId, principal, Access.READ_ONLY);

        CandlepinQuery<T> query = this.curator.getList(parent);
        return this.translator.translateQuery(query, ContentOverrideDTO.class);
    }

    private Parent verifyAndGetParent(String parentId, Principal principal, Access access) {
        // Throws exception if criteria block the id
        Parent result = this.findParentById(parentId);

        // Now that we know it exists, verify access level
        if (!principal.canAccess(result, SubResource.NONE, access)) {
            throw new ForbiddenException(i18n.tr("Insufficient permissions"));
        }

        return result;
    }
}
