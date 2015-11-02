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
package org.candlepin.audit;

import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;

import com.google.inject.Inject;


import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class is used to filter audit events so that they cannot enter
 * HornetQ queues.
 * This functionality is introduced as a hack to limit number of
 * audited events. Many users of Candlepin reported high memory usage
 * and crashes. Those issues were challenging to reproduce and
 * we believe the root cause is overload of our auditing system.
 * This this filter is used as a tool to confirm that suspicion on
 * running systems.
 *
 * This feature is implicitly disabled, use ConfigProperties.AUDIT_FILTER_ENABLED
 * to enable it.
 *
 * @author fnguyen
 */
public class EventFilter {
    private FilterPolicy defaultFilterPolicy;

    /**
     * DO_FILTER - in case inclusion and exclusion doesn't contain the event, then
     * the event will be filtered
     * DO_NOT_FILTER - the event will not be filtered
     * @author fnguyen
     *
     */
    public enum FilterPolicy {
        DO_FILTER, DO_NOT_FILTER
    }

    /**
     * Events that must not be filtered.
     */
    private Set<EventTypeAndTarget> toNotFilter = new HashSet<EventTypeAndTarget>();
    /**
     * Events that must be filtered.
     */
    private Set<EventTypeAndTarget> toFilter = new HashSet<EventTypeAndTarget>();

    private Configuration config;

    @Inject
    public EventFilter(Configuration config) {
        this.config = config;

        String policyString = config.getString(ConfigProperties.AUDIT_FILTER_DEFAULT_POLICY);

        try {
            defaultFilterPolicy = Enum.valueOf(FilterPolicy.class, policyString.trim());
        }
        catch (Exception ex) {
            throw new IllegalArgumentException("Unknown default policy settings: " + policyString);
        }

        /**
         * Initialize includes from the config file
         */
        List<String> toNotFilterConfig = config.getList(ConfigProperties.AUDIT_FILTER_DO_NOT_FILTER);
        List<String> toFilterConfig = config.getList(ConfigProperties.AUDIT_FILTER_DO_FILTER);

        fillEventTypeAndTargetFromConfig(toNotFilter, toNotFilterConfig);
        fillEventTypeAndTargetFromConfig(toFilter, toFilterConfig);
    }

    /**
     * Parses the toupes type-target from the config file.
     *
     * @param includes2
     * @param includesConfig
     */
    private void fillEventTypeAndTargetFromConfig(
            Set<EventTypeAndTarget> set, List<String> stringList) {

        for (String item : stringList) {
            if (item.trim().equals("")) {
                continue;
            }
            String[] split = item.split("-");
            if (split.length != 2) {
                throw new IllegalArgumentException("Invalid include/exclude rule: " + item +
                        "Each filter auditing" +
            "include/exclude must be in format TYPE-TARGET. For example MODIFIED-CONSUMER.");
            }
            try {
                Type type = Enum.valueOf(Type.class, split[0].trim());
                Target target = Enum.valueOf(Target.class, split[1].trim());
                set.add(new EventTypeAndTarget(type, target));
            }
            catch (Exception ex) {
                throw new IllegalArgumentException("Invalid filtering touple: " + item +
                        " . Please use only enum values of Type and Target enums, e.g. MODIFIED-CONSUMER");
            }

        }
    }

    public boolean shouldFilter(Event event) {
        boolean enabled = config.getBoolean(ConfigProperties.AUDIT_FILTER_ENABLED);
        if (!enabled) {
            return false;
        }

        EventTypeAndTarget eventKey = new EventTypeAndTarget(event.getType(),
                event.getTarget());

        if (toNotFilter.contains(eventKey)) {
            return false;
        }


        if (toFilter.contains(eventKey)) {
            return true;
        }

        if (defaultFilterPolicy == FilterPolicy.DO_FILTER) {
            return true;
        }
        else if (defaultFilterPolicy == FilterPolicy.DO_NOT_FILTER) {
            return false;
        }
        else {
            throw new IllegalArgumentException("Unknown default filtering policy: " +
        defaultFilterPolicy);
        }
    }


    private static class EventTypeAndTarget {

        private Type type;
        private Target target;

        EventTypeAndTarget(Type type, Target target) {
            super();
            this.type = type;
            this.target = target;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result +
                    ((target == null) ? 0 : target.hashCode());
            result = prime * result + ((type == null) ? 0 : type.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            EventTypeAndTarget other = (EventTypeAndTarget) obj;
            if (target == null) {
                if (other.target != null) {
                    return false;
                }
            }
            else if (!target.equals(other.target)) {
                return false;
            }
            if (type == null) {
                if (other.type != null) {
                    return false;
                }
            }
            else if (!type.equals(other.type)) {
                return false;
            }
            return true;
        }
    }
}
