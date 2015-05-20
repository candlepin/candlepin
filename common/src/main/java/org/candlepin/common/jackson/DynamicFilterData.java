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
package org.candlepin.common.jackson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**
 * DynamicFilterData
 *
 * Class to hold filtering data to be passed from DynamicFilterInterceptor
 * to DynamicPropertyFilter
 */
public class DynamicFilterData {
    private static Logger log = LoggerFactory.getLogger(DynamicFilterData.class);

    private static class Match {
        private final int level;
        private final boolean exact;

        public Match(int level, boolean exact) {
            this.level = level;
            this.exact = exact;
        }

        public int getLevel() {
            return this.level;
        }

        public boolean isExact() {
            return this.exact;
        }
    }


    private Map<String, List<String>> includeFilters;
    private Map<String, List<String>> excludeFilters;
    private boolean whitelist;

    public DynamicFilterData() {
        this(false);
    }

    public DynamicFilterData(boolean whitelist) {
        this.includeFilters = new HashMap<String, List<String>>();
        this.excludeFilters = new HashMap<String, List<String>>();
        this.whitelist = whitelist;
    }

    public void setWhitelistMode(boolean whitelist) {
        this.whitelist = whitelist;
    }

    public void includeAttribute(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }

        this.addAttributeFilter(this.includeFilters, path);
    }

    public void excludeAttribute(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }

        this.addAttributeFilter(this.excludeFilters, path);
    }

    private void addAttributeFilter(Map<String, List<String>> filters, String path) {
        String[] chunklets = path.split("\\.");
        filters.put(path.toLowerCase(), Arrays.asList(chunklets));
    }

    public boolean isAttributeExcluded(String path) {
        String[] chunklets = path.split("\\.");
        return this.isAttributeExcluded(Arrays.asList(chunklets));
    }

    public boolean isAttributeExcluded(List<String> path) {
        Match iLevel = this.getFilterLevel(this.includeFilters, path);
        Match eLevel = this.getFilterLevel(this.excludeFilters, path);

        if (iLevel.isExact() && iLevel.getLevel() > eLevel.getLevel()) {
            return false;
        }

        if (eLevel.isExact() && eLevel.getLevel() > iLevel.getLevel()) {
            return true;
        }

        return this.whitelist && (iLevel.getLevel() < 1 || iLevel.getLevel() < eLevel.getLevel());
    }

    private Match getFilterLevel(Map<String, List<String>> filters, List<String> path) {
        int level = 0;
        boolean exact = false;

        for (List<String> fpath : filters.values()) {
            boolean match = false;
            int i = 0;

            do {
                String fchunk = fpath.get(i);
                String pchunk = path.get(i);

                match = fchunk != null && fchunk.equalsIgnoreCase(pchunk);
                ++i;
            } while (match && i < path.size() && i < fpath.size());

            if (match && i > level) {
                level = i + 1;
                exact = i >= fpath.size();

                if (exact) {
                    break;
                }
            }
        }

        return new Match(level, exact);
    }
}
