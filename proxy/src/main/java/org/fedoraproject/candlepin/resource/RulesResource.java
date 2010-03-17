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

import org.fedoraproject.candlepin.model.Rules;
import org.fedoraproject.candlepin.model.RulesCurator;

import com.google.inject.Inject;
import org.apache.log4j.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.codec.binary.Base64;

/**
 * Rules API entry path
 */
@Path("/rules")
public class RulesResource {

    private static Logger log = Logger.getLogger(CertificateResource.class);
    private RulesCurator rulesCurator;

    /**
     * Default ctor
     * @param rulesCurator Curator used to interact with Rules.
     */
    @Inject
    public RulesResource(RulesCurator rulesCurator) {
        this.rulesCurator = rulesCurator;
    }

    /**
     * Uploads new Rules, returns a copy of the uploaded rules.
     * @param rulesBuffer rules to upload.
     * @return a copy of the uploaded rules.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    @Produces({ MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON,
                MediaType.APPLICATION_XML })
    public String upload(String rulesBuffer) {

        if (rulesBuffer == null || "".equals(rulesBuffer)) {
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }

        Rules rules = null;
        try {
            String decoded = new String(Base64.decodeBase64(rulesBuffer));
            rules = new Rules(decoded);
        }
        catch (Throwable t) {
            log.error("Exception in rules upload", t);
            throw new BadRequestException(
                "Error decoding the rules. The text should be base 64 encded");
        }
        rulesCurator.update(rules);
        return rulesBuffer;
    }

    /**
     * return a the rules as a string.
     * @return a the rules as a string.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN,
        MediaType.APPLICATION_XML })
    public String get() {
        String rules = rulesCurator.getRules().getRules();
        if ((rules != null) && (rules.length() > 0)) {
            System.out.println(rules);
            return Base64.encodeBase64String(rules.getBytes());
        }
        else {
            return "";
        }
    }
}
