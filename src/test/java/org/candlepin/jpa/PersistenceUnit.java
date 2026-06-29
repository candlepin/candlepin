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

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;
import java.util.Optional;

/**
 * Represents a single persistence-unit configuration from persistence.xml.
 *
 * @param name
 *  name used in code to reference this persistence unit
 *
 * @param transactionType
 *  type of transactions used by EntityManagers from this persistence unit
 *
 * @param description
 *  description of this persistence unit
 *
 * @param provider
 *  provider class that supplies EntityManagers for this persistence unit
 *
 * @param jtaDataSource
 *  the container-specific name of the JTA datasource to use
 *
 * @param nonJtaDataSource
 *  the container-specific name of a non-JTA datasource to use
 *
 * @param mappingFiles
 *  files containing mapping information. Loaded as a resource by the persistence provider
 *
 * @param jarFiles
 *  jar files that are to be scanned for managed classes
 *
 * @param classes
 *  managed class to be included in the persistence unit and to scan for annotations
 *
 * @param excludeUnlistedClasses
 *  when set to true then only listed classes and jars will be scanned for persistent classes, otherwise the
 *  enclosing jar or directory will also be scanned. Not applicable to Java SE persistence units
 *
 * @param sharedCacheMode
 *  defines whether caching is enabled for the persistence unit if caching is supported by the persistence
 *  provider. When set to ALL, all entities will be cached. When set to NONE, no entities will be cached.
 *  When set to ENABLE_SELECTIVE, only entities specified as cacheable will be cached. When set to
 *  DISABLE_SELECTIVE, entities specified as not cacheable will not be cached. When not specified or when
 *  set to UNSPECIFIED, provider defaults may apply
 *
 * @param validationMode
 *  the validation mode to be used for the persistence unit
 *
 * @param properties
 *  a list of standard and vendor-specific properties and hints
 */
public record PersistenceUnit(
    @JacksonXmlProperty(isAttribute = true)
    String name,

    @JacksonXmlProperty(isAttribute = true, localName = "transaction-type")
    TransactionType transactionType,

    String description,

    String provider,

    @JacksonXmlProperty(localName = "jta-data-source")
    String jtaDataSource,

    @JacksonXmlProperty(localName = "non-jta-data-source")
    String nonJtaDataSource,

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "mapping-file")
    List<String> mappingFiles,

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "jar-file")
    List<String> jarFiles,

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "class")
    List<String> classes,

    @JacksonXmlProperty(localName = "exclude-unlisted-classes")
    Boolean excludeUnlistedClasses,

    @JacksonXmlProperty(localName = "shared-cache-mode")
    SharedCacheMode sharedCacheMode,

    @JacksonXmlProperty(localName = "validation-mode")
    ValidationMode validationMode,

    @JacksonXmlElementWrapper(localName = "properties")
    @JacksonXmlProperty(localName = "properties")
    List<Property> properties
){
    /**
     * Retrieves the property value based on the provided key.
     *
     * @param key
     *  the key of the property that contains the returned value
     *
     * @return an optional containing the propery value or an empty optional if a property with the provided
     *  key cannot be found
     */
    public Optional<String> getPropertyValue(String key) {
        if (key == null || this.properties == null) {
            return Optional.empty();
        }

        for (Property property : properties) {
            if (key.equalsIgnoreCase(property.name())) {
                return Optional.of(property.value());
            }
        }

        return Optional.empty();
    }

    /**
     *  A name-value pair.
     *
     * @param name
     *  name of the property
     *
     * @param value
     *  value of the property
     */
    public static record Property(
        @JacksonXmlProperty(isAttribute = true)
        String name,

        @JacksonXmlProperty(isAttribute = true)
        String value
    ) {}

    /**
     * Type of transactions used by EntityManagers from this persistence unit.
     */
    public enum TransactionType {
        JTA,
        RESOURCE_LOCAL
    }

    /**
     * Defines whether caching is enabled for the persistence unit if caching is supported by the persistence
     * provider. When set to ALL, all entities will be cached. When set to NONE, no entities will be cached.
     * When set to ENABLE_SELECTIVE, only entities specified as cacheable will be cached. When set to
     * DISABLE_SELECTIVE, entities specified as not cacheable will not be cached. When not specified or when
     * set to UNSPECIFIED, provider defaults may apply.
     */
    public enum SharedCacheMode {
        ALL,
        NONE,
        ENABLE_SELECTIVE,
        DISABLE_SELECTIVE,
        UNSPECIFIED
    }

    /**
     * The validation mode to be used for the persistence unit.
     */
    public enum ValidationMode {
        AUTO,
        CALLBACK,
        NONE
    }
}

