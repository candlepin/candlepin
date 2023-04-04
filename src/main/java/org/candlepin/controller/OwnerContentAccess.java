/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.controller;

import java.util.Objects;

public class OwnerContentAccess {

    private final String contentAccessMode;
    private final String contentAccessModeList;

    public OwnerContentAccess(String contentAccessMode, String contentAccessModeList) {
        this.contentAccessMode = contentAccessMode;
        this.contentAccessModeList = contentAccessModeList;
    }

    public String getContentAccessMode() {
        return contentAccessMode;
    }

    public String getContentAccessModeList() {
        return contentAccessModeList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        OwnerContentAccess that = (OwnerContentAccess) o;
        return Objects.equals(contentAccessMode, that.contentAccessMode) &&
            Objects.equals(contentAccessModeList, that.contentAccessModeList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentAccessMode, contentAccessModeList);
    }

    @Override
    public String toString() {
        return "OwnerContentAccess{" +
            "contentAccessMode='" + contentAccessMode + '\'' +
            ", contentAccessModeList='" + contentAccessModeList + '\'' +
            '}';
    }
}
