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

import org.candlepin.exceptions.ServiceUnavailableException;
import org.candlepin.model.StatisticCurator;

import com.google.inject.Inject;

import org.hibernate.HibernateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import javax.ws.rs.PUT;
import javax.ws.rs.Path;

/**
 * Rules API entry path
 */
@Path("/statistics/generate")
public class StatisticResource {
    private static Logger log = LoggerFactory.getLogger(StatisticResource.class);
    private StatisticCurator statisticCurator;
    private I18n i18n;

    /**
     * Default ctor
     * @param statisticCurator Curator used to interact with Statistics.
     */
    @Inject
    public StatisticResource(StatisticCurator statisticCurator, I18n i18n) {
        this.statisticCurator = statisticCurator;
        this.i18n = i18n;
    }

    /**
     * Gathers statistics in system
     * <p>
     * Records them in Statistic History table
     *
     * @httpcode 503
     * @httpcode 200
     */
    @PUT
    public void execute() {

        try {
            statisticCurator.executeStatisticRun();
        }
        catch (HibernateException e) {
            log.error("Cannot store: ", e);
            throw new ServiceUnavailableException(i18n.tr("couldn't generate statistics"));
        }
        log.info("Successful statistic generation");
    }

}
