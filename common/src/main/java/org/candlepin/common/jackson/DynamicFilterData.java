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

    private Map<String, List<String>> filters;
    private boolean excluding = true;

    public DynamicFilterData(boolean excluding) {
        this.filters = new HashMap<String, List<String>>();
        this.excluding = excluding;
    }

    public void addAttributeFilter(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }

        String[] chunklets = path.split("\\.");
        this.filters.put(path.toLowerCase(), Arrays.asList(chunklets));
    }

    public boolean isAttributeExcluded(String path) {
        String[] chunklets = path.split("\\.");
        return this.isAttributeExcluded(Arrays.asList(chunklets));
    }

    public boolean isAttributeExcluded(List<String> path) {
        boolean match = false;

        for (List<String> apath : this.filters.values()) {
            int i = 0;
            do {
                String achunk = apath.get(i);
                String pchunk = path.get(i);

                match = achunk != null && achunk.equalsIgnoreCase(pchunk);
                ++i;
            } while (match && i < path.size() && i < apath.size());

            if (match && (!this.excluding || i >= apath.size())) {
                break;
            }

            match = false;
        }

        return this.excluding ? match : !match;
    }
}
