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
package org.candlepin.pinsetter.tasks;

import java.util.Set;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.google.inject.Inject;

import org.candlepin.controller.PoolManager;
import org.candlepin.model.Environment;

/**
 * RegenEnvEntitlementCertsJob
 *
 * Regenerates entitlements within an environment which are affected by the
 * promotion/demotion of the given content sets.
 */
public class RegenEnvEntitlementCertsJob extends KingpinJob {

    private PoolManager poolManager;
    public static final String ENV = "env_id";
    public static final String CONTENT = "content_ids";
    public static final String LAZY_REGEN = "lazy_regen";

    @Inject
    public RegenEnvEntitlementCertsJob(PoolManager poolManager) {
        this.poolManager = poolManager;
    }

    @Override
    public void toExecute(JobExecutionContext arg0) throws JobExecutionException {
        Environment env = (Environment) arg0.getJobDetail().getJobDataMap().get(
            ENV);
        Set<String> contentIds = (Set<String>)
            arg0.getJobDetail().getJobDataMap().get(CONTENT);
        Boolean lazy = arg0.getMergedJobDataMap().getBoolean(LAZY_REGEN);

        this.poolManager.regenerateCertificatesOf(env, contentIds, lazy);
    }
}
