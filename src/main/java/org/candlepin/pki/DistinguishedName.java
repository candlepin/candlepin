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
 * <p><br>
 * Only CN and O attributes are currently supporter. These get formatted into
 * a DN string such as "CN=name, O=org".
 *
 * @param commonName an attribute used for CN
 * @param organizationName an attribute used for O
 */
public record DistinguishedName(String commonName, String organizationName) {

    public DistinguishedName {
        if (StringUtils.isBlank(commonName) && StringUtils.isBlank(organizationName)) {
            throw new IllegalArgumentException("DN requires at least one attribute!");
        }

        if (StringUtils.isNotBlank(commonName)) {
            commonName = "CN=" + commonName;
        }
        if (StringUtils.isNotBlank(organizationName)) {
            organizationName = "O=" + organizationName;
        }
    }

    public DistinguishedName(String commonName) {
        this(commonName, (String) null);
    }

    public DistinguishedName(String commonName, Owner owner) {
        this(commonName, owner == null ? null : owner.getKey());
    }

    /**
     * Method uses DN attributes to produce a DN string such as "CN=name, O=org".
     *
     * @return DN string
     */
    public String value() {
        return Stream.of(this.commonName, this.organizationName)
            .filter(Objects::nonNull)
            .collect(Collectors.joining(", "));
    }

}
