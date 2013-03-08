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
package org.candlepin.policy.criteria;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.candlepin.config.Config;
import org.candlepin.exceptions.IseException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.policy.js.JsRunner;
import org.hibernate.criterion.Criterion;

import com.google.inject.Inject;

/**
 * RulesCriteria
 *
 * A class used to generate database criteria for filtering
 * out rules that are not applicable for a consumer before
 * running them through a rules check.
 *
 */
public class CriteriaRules  {
    protected Logger rulesLogger = null;

    protected JsRunner jsRules;
    protected Config config;
    protected ConsumerCurator consumerCurator;
    private static Logger log = Logger.getLogger(CriteriaRules.class);

    private static String jsNameSpace = "criteria_name_space";
    @Inject
    public CriteriaRules(JsRunner jsRules, Config config,
            ConsumerCurator consumerCurator) {
        this.jsRules = jsRules;
        this.config = config;
        this.consumerCurator = consumerCurator;
        this.rulesLogger = Logger.getLogger(
            CriteriaRules.class.getCanonicalName() + ".rules");
        jsRules.init(jsNameSpace);
    }


    /**
     * Create a List of jpa criterion that can filter out pools
     *         that are not applicable to consumer
     *
     * @param consumer The consumer we are filtering pools for
     * @return List of Criterion
     */
    public List<Criterion> availableEntitlementCriteria(Consumer consumer) {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("standalone", config.standalone());
        args.put("log", rulesLogger);
        args.put("consumer", consumer);

        // avoid passing in a consumerCurator just to get the host
        // consumer uuid
        Consumer hostConsumer = null;
        if (consumer.getFact("virt.uuid") != null) {
            hostConsumer = consumerCurator.getHost(consumer.getFact("virt.uuid"));
        }
        args.put("hostConsumer", hostConsumer);

        List<Criterion> poolsCriteria = null;
        try {
            poolsCriteria = jsRules.invokeMethod("poolCriteria", args);
        }
        catch (NoSuchMethodException e) {
            log.error("Unable to find javascript method: poolCriteria", e);
            throw new IseException("Unable to create pool criteria.");
        }
        return poolsCriteria;
    }



}
