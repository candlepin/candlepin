/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.jpa;

import com.fasterxml.jackson.annotation.JsonRootName;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;
import java.util.Optional;

/**
 * Represents the root persistence element from the persistence.xml.
 *
 * @param version
 *  the version of the file
 *
 * @param persistenceUnits
 *  the persistence units defined for this persistence.xml file
 */
@JsonRootName("persistence")
public record PersistenceXml(
    @JacksonXmlProperty(isAttribute = true)
    String version,

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "persistence-unit")
    List<PersistenceUnit> persistenceUnits
) {
    /**
     * Retrieves a persistence unit based on the provided name. The unit name comparison is case-sensitive.
     *
     * @param unitName
     *  the name of the persistence unit to retrieve
     *
     * @return an optional containing the persistence unit or an empty optional if the unit cannot be found
     */
    public Optional<PersistenceUnit> getUnit(String unitName) {
        if (unitName == null) {
            return Optional.empty();
        }

        for (PersistenceUnit unit : this.persistenceUnits()) {
            if (unitName.equals(unit.name())) {
                return Optional.of(unit);
            }
        }

        return Optional.empty();
    }

}



