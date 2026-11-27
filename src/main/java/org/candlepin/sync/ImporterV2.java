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

import org.candlepin.controller.ContentAccessMode;
import org.candlepin.controller.RefresherFactory;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.manifest.v1.CdnDTO;
import org.candlepin.dto.manifest.v1.ConsumerTypeDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.service.impl.ImportSubscriptionServiceAdapter;
import org.candlepin.service.model.CertificateInfo;

import com.google.inject.persist.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.xnap.commons.i18n.I18n;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.inject.Inject;

/**
 * Transactional v2 manifest import using mandatory signature and certificate integrations
 * <p>
 * Verification and detached data validation precede persistence. Consumer and metadata conflict handling,
 * unlimited subscriptions, and pool refresh share existing import models without v1 entitlement parsing.
 */
public class ImporterV2 {
    private final ManifestV2SignatureVerifier verifier;
    private final ManifestV2IdentityCertificateReader identityReader;
    private final ManifestV2ScaCertificateReader scaReader;
    private final ManifestV2CertificateInstaller certificateInstaller;
    private final ManifestV2DataReader dataReader;
    private final OwnerCurator ownerCurator;
    private final ConsumerImporter consumerImporter;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final ManifestMetadataValidator metadataValidator;
    private final ExporterMetadataCurator metadataCurator;
    private final SubscriptionReconcilerV2 reconciler;
    private final RefresherFactory refresherFactory;
    private final CdnCurator cdnCurator;
    private final ModelTranslator translator;
    private final I18n i18n;

    /**
     * Initializes the complete v2 pipeline with required integrations
     *
     * @param verifier
     *     the manifest signature and trust verifier
     * @param identityReader
     *     the upstream identity certificate reader
     * @param scaReader
     *     the upstream SCA certificate reader
     * @param certificateInstaller
     *     certificate ownership validation and transactional storage
     * @param dataReader
     *     the detached manifest payload reader
     * @param ownerCurator
     *     the owner locking and persistence dependency
     * @param consumerImporter
     *     the shared consumer conflict and metadata importer
     * @param consumerTypeCurator
     *     local consumer type resolution
     * @param metadataValidator
     *     shared export timestamp conflict checks
     * @param metadataCurator
     *     export timestamp persistence
     * @param reconciler
     *     upstream pool identity reconciliation
     * @param refresherFactory
     *     pool refresh using imported subscriptions
     * @param cdnCurator
     *     local CDN label resolution
     * @param translator
     *     local model-to-import DTO translation
     * @param i18n
     *     the import error message translator
     */
    @Inject
    public ImporterV2(ManifestV2SignatureVerifier verifier, ManifestV2IdentityCertificateReader identityReader,
        ManifestV2ScaCertificateReader scaReader, ManifestV2CertificateInstaller certificateInstaller,
        ManifestV2DataReader dataReader, OwnerCurator ownerCurator, ConsumerImporter consumerImporter,
        ConsumerTypeCurator consumerTypeCurator, ManifestMetadataValidator metadataValidator,
        ExporterMetadataCurator metadataCurator, SubscriptionReconcilerV2 reconciler,
        RefresherFactory refresherFactory, CdnCurator cdnCurator, ModelTranslator translator, I18n i18n) {
        this.verifier = Objects.requireNonNull(verifier);
        this.identityReader = Objects.requireNonNull(identityReader);
        this.scaReader = Objects.requireNonNull(scaReader);
        this.certificateInstaller = Objects.requireNonNull(certificateInstaller);
        this.dataReader = Objects.requireNonNull(dataReader);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.consumerImporter = Objects.requireNonNull(consumerImporter);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.metadataValidator = Objects.requireNonNull(metadataValidator);
        this.metadataCurator = Objects.requireNonNull(metadataCurator);
        this.reconciler = Objects.requireNonNull(reconciler);
        this.refresherFactory = Objects.requireNonNull(refresherFactory);
        this.cdnCurator = Objects.requireNonNull(cdnCurator);
        this.translator = Objects.requireNonNull(translator);
        this.i18n = Objects.requireNonNull(i18n);
    }

    /**
     * Detects a version 2 manifest and returns its extracted payload directory
     *
     * @param stagingDirectory
     *     the temporary import staging directory
     * @param payload
     *     the extracted outer archive directory
     * @param archive
     *     the original uploaded archive
     * @throws IOException
     *     if archive or metadata files cannot be read or extracted
     * @throws ImporterException
     *     if an explicit manifest format version is invalid or extraction escapes the target directory
     * @return
     *     the extracted v2 payload directory if the archive is a v2 manifest, or empty if it is v1
     */
    public Optional<Path> extractPayload(Path stagingDirectory, Path payload, Path archive)
        throws IOException, ImporterException {
        Objects.requireNonNull(stagingDirectory);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(archive);

        Path metadata = payload.resolve(ManifestConstants.META_JSON);
        if (Files.isRegularFile(metadata) && this.dataReader.isManifestV2(metadata)) {
            return Optional.of(payload);
        }

        Path consumerExport = payload.resolve(ManifestConstants.CONSUMER_EXPORT_ZIP);
        if (Files.isRegularFile(consumerExport) && this.isNestedManifestV2(consumerExport)) {
            return Optional.of(this.extractManifestV2(stagingDirectory, archive));
        }

        return Optional.empty();
    }

    private boolean isNestedManifestV2(Path archive) throws SyncDataFormatException {
        try (FileSystem filesystem = FileSystems.newFileSystem(archive)) {
            Path metadata = filesystem.getPath("/", ManifestConstants.META_JSON);
            if (!Files.isRegularFile(metadata)) {
                metadata = filesystem.getPath("/export", ManifestConstants.META_JSON);
            }
            if (!Files.isRegularFile(metadata)) {
                return false;
            }
            return this.dataReader.isManifestV2(metadata);
        }
        catch (IOException | ProviderNotFoundException e) {
            return false;
        }
    }

    private Path extractManifestV2(Path stagingDirectory, Path archive) throws IOException, ImportExtractionException {
        Path destination = Files.createDirectory(stagingDirectory.resolve("v2-payload"));
        try (FileSystem envelope = FileSystems.newFileSystem(archive);
            FileSystem filesystem = FileSystems.newFileSystem(
                envelope.getPath("/", ManifestConstants.CONSUMER_EXPORT_ZIP))) {
            Path root = filesystem.getPath("/");
            try (Stream<Path> entries = Files.walk(root)) {
                for (Path entry : entries.toList()) {
                    Path target = destination.resolve(root.relativize(entry).toString()).normalize();
                    if (!target.startsWith(destination)) {
                        throw new ImportExtractionException(
                            this.i18n.tr("Entry is outside of the target directory"));
                    }
                    if (Files.isDirectory(entry)) {
                        Files.createDirectories(target);
                    }
                    else {
                        Files.copy(entry, target);
                    }
                }
            }
        }
        return Files.isRegularFile(destination.resolve(ManifestConstants.META_JSON)) ? destination :
            destination.resolve("export");
    }

    /**
     * Verified SCA-only import with rollback on payload, certificate, conflict, or refresh failures
     * <p>
     * The caller owns extraction cleanup, import records, and events. All database writes performed here,
     * including the certificate installer and pool refresh, participate in the same transaction.
     *
     * @param owner
     *     the local receiving owner
     * @param archive
     *     the original, unchanged uploaded archive
     * @param directory
     *     the safely extracted payload directory
     * @param overrides
     *     explicit import conflict overrides
     * @throws IOException
     *     if manifest payload files cannot be read
     * @throws ImporterException
     *     if verification, certificate validation, payload validation, or conflict checks fail
     * @return
     *     imported data for the shared import record and event workflow
     */
    @Transactional(rollbackOn = { IOException.class, ImporterException.class, RuntimeException.class })
    public ManifestV2Data importObjects(Owner owner, Path archive, Path directory, ConflictOverrides overrides)
        throws IOException, ImporterException {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(archive);
        Objects.requireNonNull(directory);
        Objects.requireNonNull(overrides);
        this.verifier.verify(archive, directory, overrides);
        ManifestV2Data data = this.dataReader.read(directory, owner);
        CertificateInfo identity = this.identityReader.read(directory);
        CertificateInfo sca = this.scaReader.read(directory);
        if (identity == null || sca == null) {
            throw new SyncDataFormatException(this.i18n.tr("Required v2 certificate material is missing"));
        }
        this.certificateInstaller.validate(data.getConsumer(), identity, sca);
        this.ownerCurator.lock(owner);
        ConsumerType type = this.consumerTypeCurator.getByLabel(data.getConsumer().getType().getLabel());
        if (type == null || !type.isManifest()) {
            throw new SyncDataFormatException(this.i18n.tr("Unknown or invalid upstream consumer type"));
        }
        data.getConsumer().setType(this.translator.translate(type, ConsumerTypeDTO.class));
        List<ImportConflictException> conflicts = new ArrayList<>();
        ExporterMetadata metadata = null;
        try {
            metadata = this.metadataValidator.validate(ExporterMetadata.TYPE_PER_USER, owner, data.getMeta(), overrides);
        }
        catch (ImportConflictException e) {
            conflicts.add(e);
        }
        try {
            this.consumerImporter.store(owner, data.getConsumer(), overrides, null);
        }
        catch (ImportConflictException e) {
            conflicts.add(e);
        }
        if (!conflicts.isEmpty()) {
            throw new ImportConflictException(conflicts);
        }
        owner.setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());
        this.certificateInstaller.install(owner, identity, sca);
        this.metadataCurator.saveOrUpdateAll(List.of(metadata), true, false);
        List<SubscriptionDTO> subscriptions = data.getSubscriptions();
        if (StringUtils.isNotBlank(data.getMeta().getCdnLabel())) {
            Cdn cdn = this.cdnCurator.getByLabel(data.getMeta().getCdnLabel());
            if (cdn != null) {
                CdnDTO dto = this.translator.translate(cdn, CdnDTO.class);
                subscriptions.forEach(subscription -> subscription.setCdn(dto));
            }
        }
        this.reconciler.reconcile(owner, subscriptions);
        this.refresherFactory.getRefresher(new ImportSubscriptionServiceAdapter(subscriptions))
            .add(owner)
            .run();
        return data;
    }
}
