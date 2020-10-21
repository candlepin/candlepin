/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

package org.candlepin.dto.api.v1;

import java.util.List;

public class ContentAccessDTO {

    private String contentAccessMode;
    private List<String> contentAccessModeList;

    public String getContentAccessMode() {
        return contentAccessMode;
    }

    public ContentAccessDTO contentAccessMode(String mode) {
        this.contentAccessMode = mode;
        return this;
    }

    public List<String> getContentAccessModeList() {
        return contentAccessModeList;
    }

    public ContentAccessDTO contentAccessModeList(List<String> modeList) {
        this.contentAccessModeList = modeList;
        return this;
    }
}
