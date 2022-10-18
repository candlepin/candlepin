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

import org.candlepin.dto.api.client.v1.CdnDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

/**
 * Class meant to provide fully randomized instances of Cdns.
 *
 * Individual tests can then modify the instance according to their needs.
 */
public final class Cdns {

    private Cdns() {
        throw new UnsupportedOperationException();
    }

    public static CdnDTO random() {
        CdnDTO newCdn = new CdnDTO();
        newCdn.setLabel(StringUtil.random("cdn-label"));
        newCdn.setName(StringUtil.random("cdn-name"));
        newCdn.setUrl(StringUtil.random("https://cdn.test.com/apiUrl/"));
        return newCdn;
    }

    public static ExportCdn toExport(CdnDTO cdn) {
        return new ExportCdn(
            cdn.getLabel(),
            StringUtil.random("https://cdn.test.com/webUrl/"),
            cdn.getUrl()
        );
    }

}
