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

import org.candlepin.audit.EventSink;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ServiceUnavailableException;
import org.candlepin.model.CuratorException;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;

import com.google.inject.Inject;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Rules API entry path
 */
@Path("/rules")
public class RulesResource {
    private static Logger log = LoggerFactory.getLogger(RulesResource.class);
    private RulesCurator rulesCurator;
    private I18n i18n;
    private EventSink sink;

    /**
     * Default ctor
     * @param rulesCurator Curator used to interact with Rules.
     */
    @Inject
    public RulesResource(RulesCurator rulesCurator,
        I18n i18n, EventSink sink) {
        this.rulesCurator = rulesCurator;
        this.i18n = i18n;
        this.sink = sink;
    }

    /**
     * Uploads the Rules
     * <p>
     * Returns a copy of the uploaded rules.
     *
     * @param rulesBuffer rules to upload.
     * @return a String object
     * @httpcode 400
     * @httpcode 200
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    @Produces({ MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON })
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
                i18n.tr("Error decoding the rules. The text should be base 64 encoded"));
        }
        Rules oldRules = rulesCurator.getRules();
        rulesCurator.update(rules);
        sink.emitRulesModified(oldRules, rules);
        return rulesBuffer;
    }

    /**
     * Retrieves the Rules
     *
     * @return a String object
     * @httpcode 503
     * @httpcode 200
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    public String get() {
        try {
            String rules = rulesCurator.getRules().getRules();
            if ((rules != null) && (rules.length() > 0)) {
                return Base64.encodeBase64String(rules.getBytes());
            }
            return "";
        }
        catch (CuratorException e) {
            log.error("couldn't read rules file", e);
            throw new ServiceUnavailableException(i18n.tr("couldn't read rules file"));
        }
    }

    /**
     * Removes the Rules
     * <p>
     * Deletes any uploaded rules, uses bundled rules instead
     *
     * @httpcode 200
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public void delete() {
        Rules deleteRules = rulesCurator.getRules();
        rulesCurator.delete(deleteRules);
        sink.emitRulesDeleted(deleteRules);
    }
}
