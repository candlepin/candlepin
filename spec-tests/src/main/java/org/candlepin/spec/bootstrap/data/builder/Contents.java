/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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
package org.candlepin.spec.bootstrap.data.builder;

import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

/**
 * Class meant to provide fully randomized instances of content.
 *
 * Individual tests can then modify the instance according to their needs.
 */
public final class Contents {

    private Contents() {
        throw new UnsupportedOperationException();
    }

    public static ContentDTO random() {
        ContentDTO newContent = new ContentDTO();
        newContent.setId(StringUtil.random("cid"));
        newContent.setName(StringUtil.random("cname"));
        newContent.setLabel(StringUtil.random("clabel"));
        newContent.setType(StringUtil.random("ctype"));
        newContent.setVendor(StringUtil.random("cvendor1"));
        return newContent;
    }

}
