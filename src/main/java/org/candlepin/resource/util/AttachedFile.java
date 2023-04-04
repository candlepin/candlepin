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
package org.candlepin.resource.util;

import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MultivaluedMap;



/**
 * The AttachedFile utility class performs the boilerplate code for fetching the filename and file
 * for a given request.
 */
public class AttachedFile {

    /**
     * Regex to pull the filename out of the content disposition header, if present. This regex
     * has two groups:
     *  - group 1 determines if the filename is quoted or not; it will be empty or a lone
     *    double-quote character
     *  - group 2 is the filename
     *
     * The regex handles most, if not all, instances of escape sequences; but always applies
     * them, even in cases where the filename itself is not quoted.
     */
    private static final Pattern CONTENT_DISPOSITION_REGEX = Pattern
        .compile("(?i:filename)\\*?\\s*=\\s*(\\\"?+)((?:(?:\\\\.)*+[^\\\"\\\\]*+)+)\\1");

    private static final GenericType<File> FILE_TYPE = new GenericType<File>() {};
    private static final GenericType<InputStream> INPUT_STREAM_TYPE = new GenericType<InputStream>() {};

    private final InputPart part;
    private final String filename;

    /**
     * Creates a new AttachedFile instance backed by the given input part, assigned the given
     * filename.
     *
     * @param part
     *  the source InputPart instance to use for this attached file; cannot be null
     *
     * @param filename
     *  the name of the attached file, if provided
     *
     * @throws NullPointerException
     *  if the input part is null
     */
    private AttachedFile(InputPart part, String filename) {
        this.part = Objects.requireNonNull(part);
        this.filename = filename;
    }

    /**
     * Returns the filename for this attached file, if present. If the filename was not set by the
     * client, this method returns the default filename provided.
     *
     * @param defaultFilename
     *  the default filename to use if no filename was set by the client
     *
     * @return
     *  the filename of this attached file if specified by the client, or the provided default
     */
    public String getFilename(String defaultFilename) {
        return this.filename != null ? this.filename : defaultFilename;
    }

    /**
     * Returns the filename for this attached file, if present. If the filename was not set by the
     * client, this method returns null.
     *
     * @return
     *  the filename of this attached file if specified by the client; null otherwise
     */
    public String getFilename() {
        return this.getFilename(null);
    }

    /**
     * Returns an input stream for the attached file data.
     *
     * @throws IOException
     *  if an IO exception occurs while building the input stream for the attached file
     *
     * @return
     *  an input stream for the file data of this attached file
     */
    public InputStream getInputStream() throws IOException {
        return this.part.getBody(INPUT_STREAM_TYPE);
    }

    /**
     * Returns a file reference to the attached file data. Note that the file is likely stored in an
     * ephimeral location or repository, and should be moved out or processed as soon as possible.
     *
     * @throws IOException
     *  if an IO exception occurs while fetching a file reference for the attached file
     *
     * @return
     *  a reference to the file data of this attached file
     */
    public File getFile() throws IOException {
        return this.part.getBody(FILE_TYPE);
    }

    /**
     * Processes the given InputPart and packages the data in an AttachedFile instance.
     *
     * @param part
     *  the InputPart instance to process
     *
     * @throws IllegalArgumentException
     *  if the given InputPart is null
     *
     * @return
     *  an AttachedFile instance wrapping the given InputPart
     */
    private static AttachedFile processInputPart(InputPart part) {
        if (part == null) {
            throw new IllegalArgumentException("part is null");
        }

        MultivaluedMap<String, String> headers = part.getHeaders();

        // parse filename, if present
        String disposition = headers.getFirst("Content-Disposition");
        Matcher matcher = CONTENT_DISPOSITION_REGEX.matcher(disposition != null ? disposition : "");
        String filename = matcher.find() ? matcher.group(2) : null;

        // Package the pair up and return
        return new AttachedFile(part, filename);
    }

    /**
     * Fetches an AttachedFile instance from the index of the given MultipartInput object. If the
     * input object is null, lacks the appropriate parts, or the given index is out of bounds, this
     * function throws an exception.
     *
     * @param input
     *  the MultipartInput instance from which to pull attached files
     *
     * @param index
     *  an offset indicating which part of the multipart input to fetch
     *
     * @throws IllegalArgumentException
     *  if the MultipartInput instance is null
     *
     * @throws IllegalStateException
     *  if the MultipartInput does not contain any input parts to process
     *
     * @throws IndexOutOfBoundsException
     *  if the MultipartInput does not contain enough parts to fulfill the request
     *
     * @return
     *  an AttachedFile instance containing data from the specified input part of the provided
     *  multipart input
     */
    public static AttachedFile getAttachedFile(MultipartInput input, int index) {
        if (input == null) {
            throw new IllegalArgumentException("multipart input is null");
        }

        List<InputPart> parts = input.getParts();
        if (parts == null) {
            throw new IllegalStateException("multipart input lacks individual input parts");
        }

        if (parts.size() <= index) {
            String errmsg = String.format("requested file not present in request: %d", index);
            throw new IndexOutOfBoundsException(errmsg);
        }

        return processInputPart(parts.get(index));
    }

    /**
     * Fetches an AttachedFile instance from the given MultipartInput object, using the first
     * available part. If the input object is null or lacks any input parts, this function throws
     * an exception.
     *
     * @param input
     *  the MultipartInput instance from which to pull an attached file
     *
     * @throws IllegalArgumentException
     *  if the MultipartInput instance is null
     *
     * @throws IllegalStateException
     *  if the MultipartInput does not contain any input parts to process
     *
     * @return
     *  an AttachedFile instance containing data from the first input part of the provided multipart
     *  input
     */
    public static AttachedFile getAttachedFile(MultipartInput input) {
        return getAttachedFile(input, 0);
    }

    /**
     * Fetches a list of AttachedFiles containing the data from all of the available parts of the
     * given MultipartInput instance. The list will contain the files in the same order in which
     * they appear in the request. If the input object is null or lacks any input parts, this
     * function throws an exception.
     *
     * @param input
     *  the MultipartInput instance from which to pull attached files
     *
     * @throws IllegalArgumentException
     *  if the MultipartInput instance is null
     *
     * @throws IllegalStateException
     *  if the MultipartInput does not contain any input parts to process
     *
     * @return
     *  a list of AttachedFile instances containing data from all the input parts of the provided
     *  multipart input
     */
    public static List<AttachedFile> getAttachedFiles(MultipartInput input) {
        if (input == null) {
            throw new IllegalArgumentException("multipart input is null");
        }

        List<InputPart> parts = input.getParts();
        if (parts == null) {
            throw new IllegalStateException("multipart input lacks individual input parts");
        }

        List<AttachedFile> output = new ArrayList<>(parts.size());
        for (InputPart part : parts) {
            output.add(processInputPart(part));
        }

        return output;
    }

}
