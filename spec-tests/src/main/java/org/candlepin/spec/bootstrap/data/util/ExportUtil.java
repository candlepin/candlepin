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
package org.candlepin.spec.bootstrap.data.util;

import org.candlepin.spec.bootstrap.client.ApiClient;

import tools.jackson.core.type.TypeReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/**
 * Utility methods for managing manifest exports.
 */
public final class ExportUtil {
    private static final String EXPORT_NAME = "consumer_export.zip";

    private ExportUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * Retrieves the export zip file in the manifest.
     *
     * @param manifest
     *  compressed manifest to retrieve the export from.
     *
     * @throws IllegalArgumentException
     *  if manifest is null.
     *
     * @throws IOException
     *  if unable to create a temporary export zip file.
     *
     * @return a compressed {@link ZipFile} archive of the export.
     */
    public static ZipFile getExportArchive(File manifest) throws IOException {
        if (manifest == null) {
            throw new IllegalArgumentException("Manifest is null.");
        }

        try (ZipFile zipFile = new ZipFile(manifest)) {
            ZipEntry entry = zipFile.getEntry(EXPORT_NAME);
            File tmp = File.createTempFile("export", ".zip");
            tmp.deleteOnExit();
            try (InputStream istream = zipFile.getInputStream(entry);
                FileOutputStream ostream = new FileOutputStream(tmp)) {
                istream.transferTo(ostream);
            }

            return new ZipFile(tmp);
        }
    }

    /**
     * Deserializes a json file contained in the provided manifest {@ZipFile}.
     *
     * @param manifest
     *  manifest file that contains the json file to deserialize; cannot be null.
     *
     * @param path
     *  the fully qualified path of the json file to deserialize; cannot be null or empty.
     *
     * @param typeRef
     *  generic type reference used for deserializing the json file; cannot be null.
     *
     * @throws IllegalArgumentException
     *  if the manifest or typeRef is null, or if the path is null or empty.
     *
     * @throws IOException
     *  if unable to deserialize the json file.
     *
     * @return
     *  deserialized json file or null if the file does not exist in the manifest.
     */
    public static <T> T deserializeJsonFile(ZipFile manifest, String path, TypeReference<T> typeRef)
        throws IOException {
        if (manifest == null) {
            throw new IllegalArgumentException("Manifest is null");
        }

        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path is null or empty");
        }

        if (typeRef == null) {
            throw new IllegalArgumentException("TypeRef is null or empty");
        }

        ZipEntry entry = manifest.getEntry(path);
        if (entry == null) {
            return null;
        }

        try (InputStream istream = manifest.getInputStream(entry)) {
            return ApiClient.MAPPER.readValue(istream, typeRef);
        }
    }

    /**
     * Deserializes a json file contained in the provided manifest {@ZipFile}.
     *
     * @param manifest
     *  manifest file that contains the json file to deserialize; cannot be null.
     *
     * @param path
     *  the fully qualified path of the json file to deserialize; cannot be null or empty.
     *
     * @param type
     *  class used for deserializing the json file; cannot be null.
     *
     * @throws IllegalArgumentException
     *  if the manifest or typeRef is null, or if the path is null or empty.
     *
     * @throws IOException
     *  if unable to deserialize the json file.
     *
     * @return
     *  deserialized json file or null if the file does not exist in the manifest.
     */
    public static <T> T deserializeJsonFile(ZipFile manifest, String path, Class<T> type)
        throws IOException {
        if (manifest == null) {
            throw new IllegalArgumentException("Manifest is null");
        }

        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path is null or empty");
        }

        if (type == null) {
            throw new IllegalArgumentException("Type is null or empty");
        }

        ZipEntry entry = manifest.getEntry(path);
        if (entry == null) {
            return null;
        }

        try (InputStream istream = manifest.getInputStream(entry)) {
            return ApiClient.MAPPER.readValue(istream, type);
        }
    }

    /**
     * Extracts the given entry from the provided archive. If the entry does not exist or otherwise cannot be
     * read, this method throws an IOException.
     *
     * @param archive
     *  the archive from which to read an entry
     *
     * @param entry
     *  the entry to read
     *
     * @throws IllegalArgumentException
     *  if either archive or entry are null
     *
     * @throws IOException
     *  if the entry does not exist or otherwise cannot be read
     *
     * @return
     *  the contents of the entry as a byte array
     */
    public static byte[] extractEntry(ZipFile archive, ZipEntry entry) throws IOException {
        if (archive == null) {
            throw new IllegalArgumentException("archive is null");
        }

        if (entry == null) {
            throw new IllegalArgumentException("entry is null");
        }

        try (InputStream istream = archive.getInputStream(entry)) {
            return istream.readAllBytes();
        }
    }

}
