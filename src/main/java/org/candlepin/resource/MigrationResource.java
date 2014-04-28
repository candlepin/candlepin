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

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.pinsetter.tasks.MigrateOwnerJob;

import com.google.inject.Inject;

import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * MigrationResource
 */
@Path("/migrations")
public class MigrationResource {
    private static Logger log = LoggerFactory.getLogger(MigrationResource.class);
    public static final String OWNER = "owner";

    private I18n i18n;

    @Inject
    public MigrationResource(I18n i18n) {
        this.i18n = i18n;
    }

    /**
     * Creates a Migration
     *
     * @return a JobDetail object
     * @httpcode 400
     * @httpcode 202
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public JobDetail createMigration(@QueryParam("entity") String entity,
        @QueryParam("id") String key,
        @QueryParam("uri") String url,
        @QueryParam("delete") @DefaultValue("true") boolean delete) {

        if (OWNER.equals(entity)) {
            return migrateOwner(key, url, delete);
        }

        throw new BadRequestException(i18n.tr("Bad entity value."));
    }

    /**
     * @return a JobDetail object
     * @httpcode 202
     */
    private JobDetail migrateOwner(String ownerKey, String url, boolean delete) {

        if (log.isDebugEnabled()) {
            log.debug("launch migrate owner - owner [" + ownerKey +
                "], uri [" + url + "]");
        }

        return MigrateOwnerJob.migrateOwner(ownerKey, url, delete);
    }
}
