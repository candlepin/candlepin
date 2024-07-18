/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

package org.candlepin.pki;

import org.candlepin.model.Owner;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class represents a X509 Distinguished Name.
 *
 * @param commonName an attribute used for CN
 * @param organizationName an attribute used for O
 */
public record DistinguishedName(String commonName, String organizationName) {

    public DistinguishedName {
        if (StringUtils.isBlank(commonName) && StringUtils.isBlank(organizationName)) {
            throw new IllegalArgumentException("Distinguished name requires at least one attribute!");
        }

        if (StringUtils.isBlank(commonName)) {
            commonName = null;
        }

        if (StringUtils.isBlank(organizationName)) {
            organizationName = null;
        }
    }

    public DistinguishedName(String commonName) {
        this(commonName, (String) null);
    }

    public DistinguishedName(String commonName, Owner owner) {
        this(commonName, owner == null ? null : owner.getKey());
    }

    /**
     * Returns a string representation of this distinguished name; e.g. "O=org, CN=name".
     *
     * @return
     *  a string representation of this distinguished name
     */
    @Override
    public String toString() {
        String orgName = this.organizationName != null ? "O=" + this.organizationName : null;
        String commonName = this.commonName != null ? "CN=" + this.commonName : null;

        return Stream.of(commonName, orgName)
            .filter(Objects::nonNull)
            .collect(Collectors.joining(", "));
    }

}
