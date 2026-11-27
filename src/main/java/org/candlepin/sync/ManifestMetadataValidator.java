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

package org.candlepin.sync;

import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.Owner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Objects;

import jakarta.inject.Inject;

/**
 * Shared v1 and v2 metadata conflict checks before recording a successful import
 */
public class ManifestMetadataValidator {
    private static final Logger log = LoggerFactory.getLogger(ManifestMetadataValidator.class);
    private final ExporterMetadataCurator expMetaCurator;
    private final I18n i18n;

    /**
     * Initializes the metadata validator
     *
     * @param expMetaCurator
     *     the previous import timestamp lookup
     * @param i18n
     *     the import error message translator
     */
    @Inject
    public ManifestMetadataValidator(ExporterMetadataCurator expMetaCurator, I18n i18n) {
        this.expMetaCurator = Objects.requireNonNull(expMetaCurator);
        this.i18n = Objects.requireNonNull(i18n);
    }

    /**
     * Validation of the incoming export date against the previous import
     *
     * @param type
     *     the exporter metadata scope
     * @param owner
     *     the local owner, required for per-owner metadata
     * @param m
     *     incoming manifest metadata
     * @param forcedConflicts
     *     explicit old or same-manifest overrides
     * @throws ImporterException
     *     if metadata scope is invalid or an unresolved conflict exists
     * @return
     *     metadata ready to persist after all import conflicts have been checked
     */
    public ExporterMetadata validate(String type, Owner owner, Meta m, ConflictOverrides forcedConflicts)
        throws ImporterException {
        Objects.requireNonNull(m);
        Objects.requireNonNull(forcedConflicts);
        if (type == null) {
            throw new ImporterException(this.i18n.tr("Wrong metadata type"));
        }

        ExporterMetadata lastrun = null;
        if (ExporterMetadata.TYPE_SYSTEM.equals(type)) {
            lastrun = this.expMetaCurator.getByType(type);
        }
        else if (ExporterMetadata.TYPE_PER_USER.equals(type)) {
            if (owner == null) {
                throw new ImporterException(this.i18n.tr("Invalid owner"));
            }

            lastrun = this.expMetaCurator.getByTypeAndOwner(type, owner);
        }

        if (lastrun == null) {
            // this is our first import, let's create a new entry
            lastrun = new ExporterMetadata(type, m.getCreated(), owner);
        }
        else {
            if (lastrun.getExported().after(m.getCreated())) {
                if (!forcedConflicts.isForced(Importer.Conflict.MANIFEST_OLD)) {
                    throw new ImportConflictException(this.i18n.tr("Import is older than existing data"),
                        Importer.Conflict.MANIFEST_OLD);
                }
                else {
                    log.warn("Manifest older than existing data.");
                }
            }
            else {
                /*
                 *  Prior to 5.6.4, MySQL did not store fractions of a second in
                 *  temporal values.  Consequently, the manifest metadata can end up
                 *  with a created date that is a fraction of a second ahead of
                 *  the created date in the cp_export_metadata table.  So we throw away
                 *  the fractions of a second.
                 */
                long exported = lastrun.getExported().getTime() / 1000;
                long created = m.getCreated().getTime() / 1000;
                if (exported == created) {
                    if (!forcedConflicts.isForced(Importer.Conflict.MANIFEST_SAME)) {
                        throw new ImportConflictException(this.i18n.tr("Import is the same as existing data"),
                            Importer.Conflict.MANIFEST_SAME);
                    }
                    else {
                        log.warn("Manifest same as existing data.");
                    }
                }
            }

            lastrun.setExported(m.getCreated());
        }

        return lastrun;
    }
}
