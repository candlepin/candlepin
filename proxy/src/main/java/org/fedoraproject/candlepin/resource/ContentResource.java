/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.resource;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.ContentCurator;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
/**
 * ContentResource
 */

@Path("/content")
public class ContentResource {

    private ContentCurator contentCurator;
    private I18n i18n;

    /**
     * default ctor
     * 
     * @param prodAdapter
     *            Product Adapter used to interact with multiple services.
     */
    @Inject
    public ContentResource(ContentCurator contentCurator, 
                           I18n i18n) {
        this.i18n = i18n;
        this.contentCurator = contentCurator;
    }
    
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public List<Content> list() {
        return contentCurator.listAll();
        
    }
    
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/id/{content_id}")
    public Content getContent(@PathParam("contend_id") Long contentId) {
        Content content = contentCurator.find(contentId);
        
        if (content == null) {
            throw new BadRequestException(  
                i18n.tr("Content with id {0} could not be found", contentId));
        }
        
        return content;
        
    }
    
    @POST
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @AllowRoles(roles = {Role.SUPER_ADMIN})
    public Content createContent(Content content) {
        return contentCurator.create(content);
        
    }
    
    
}
