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

import org.candlepin.model.Persisted;

import java.util.HashMap;
import java.util.Map;



/**
 * The ImportResult class contains references to the entities which were processed by a
 * controller-level import operation.
 *
 * @param <E>
 *  The entity class contained by a given import result instance
 */
public class ImportResult<E extends Persisted> {

    private final Map<String, E> skippedEntities;
    private final Map<String, E> createdEntities;
    private final Map<String, E> updatedEntities;
    private Map<String, E> importedEntities;

    /**
     * Instantiates a new, empty ImportResult instance.
     */
    public ImportResult() {
        this.skippedEntities = new HashMap<>();
        this.createdEntities = new HashMap<>();
        this.updatedEntities = new HashMap<>();
        this.importedEntities = null;
    }

    /**
     * Retrieves a map containing the skipped entities. The entities will be mapped by their Red
     * Hat ID.
     *
     * @return
     *  A map containing all skipped entities
     */
    public Map<String, E> getSkippedEntities() {
        return this.skippedEntities;
    }

    /**
     * Retrieves a map containing the created entities. The entities will be mapped by their Red
     * Hat ID.
     *
     * @return
     *  A map containing all created entities
     */
    public Map<String, E> getCreatedEntities() {
        return this.createdEntities;
    }

    /**
     * Retrieves a map containing the updated entities. The entities will be mapped by their Red
     * Hat ID.
     *
     * @return
     *  A map containing all updated entities
     */
    public Map<String, E> getUpdatedEntities() {
        return this.updatedEntities;
    }

    /**
     * Retrieves a map containing the imported entities. The entities will be mapped by their Red
     * Hat ID.
     *
     * @return
     *  A map containing all imported entities
     */
    public Map<String, E> getImportedEntities() {
        if (this.importedEntities == null) {
            this.importedEntities = new HashMap<>();

            this.importedEntities.putAll(this.skippedEntities);
            this.importedEntities.putAll(this.createdEntities);
            this.importedEntities.putAll(this.updatedEntities);
        }

        return this.importedEntities;
    }
}
