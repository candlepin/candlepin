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

package org.candlepin.gutterball.jackson;

import org.candlepin.gutterball.model.snapshot.NonCompliantProductReference;

import com.fasterxml.jackson.databind.util.StdConverter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts a data map into a Set of product references
 */
public class NonCompliantProductReferenceConverter extends
    StdConverter<List<String>, Set<NonCompliantProductReference>> {

    @Override
    public Set<NonCompliantProductReference> convert(List<String> data) {
        Set<NonCompliantProductReference> refs = new HashSet<NonCompliantProductReference>();

        if (data != null) {
            for (String productId : data) {
                // The compliance status will be updated on update or persist.
                refs.add(new NonCompliantProductReference(null, productId));
            }
        }

        return refs;
    }

}
