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
package org.candlepin.config;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;



/**
 * Classes implementing FileConfiguration take their configuration from a file source and can
 * therefore specify an encoding.
 */
public interface FileConfiguration extends Configuration {

    /**
     * Fetches the default character set this configuration object will use to load configuration
     * data from files when no character set is provided.
     *
     * @return
     *  the default charset used when none is provided while loading configuration data
     */
    Charset getDefaultCharset();

    /**
     * Reads the configuration from the given file, clearing any currently loaded or stored
     * configuration.
     *
     * @param filename
     *  the name of the file to load
     *
     * @throws IllegalArgumentException
     *  if filename is null
     *
     * @throws ConfigurationException
     *  if the given file cannot be read for any reason
     */
    void load(String filename) throws ConfigurationException;

    /**
     * Reads the configuration from the given file, processing it with the specified encoding,
     * clearing any currently loaded or stored configuration.
     *
     * @param filename
     *  the name of the file to load
     *
     * @param encoding
     *  the character encoding to use while processing the file
     *
     * @throws IllegalArgumentException
     *  if filename is null
     *
     * @throws ConfigurationException
     *  if the given file cannot be read for any reason
     */
    void load(String filename, Charset encoding) throws ConfigurationException;

    /**
     * Reads the configuration from the given file, clearing any currently loaded or stored
     * configuration.
     *
     * @param file
     *  a File object representing the file to load
     *
     * @throws IllegalArgumentException
     *  if file is null
     *
     * @throws ConfigurationException
     *  if the given file cannot be read for any reason
     */
    void load(File file) throws ConfigurationException;

    /**
     * Reads the configuration from the given file, processing it with the specified encoding,
     * clearing any currently loaded or stored configuration.
     *
     * @param file
     *  a File object representing the file to load
     *
     * @param encoding
     *  the character encoding to use while processing the file
     *
     * @throws IllegalArgumentException
     *  if file is null
     *
     * @throws ConfigurationException
     *  if the given file cannot be read for any reason
     */
    void load(File file, Charset encoding) throws ConfigurationException;

    /**
     * Reads the configuration from the given input stream, clearing any currently loaded or stored
     * configuration.
     *
     * @param istream
     *  an input stream from which to read configuration
     *
     * @throws IllegalArgumentException
     *  if istream is null
     *
     * @throws ConfigurationException
     *  if the given input stream cannot be read for any reason
     */
    void load(InputStream istream) throws ConfigurationException;

    /**
     * Reads the configuration from the given input stream, processing it with the specified
     * encoding, clearing any currently loaded or stored configuration.
     *
     * @param istream
     *  an input stream from which to read configuration
     *
     * @param encoding
     *  the character encoding to use while processing the stream
     *
     * @throws IllegalArgumentException
     *  if istream is null
     *
     * @throws ConfigurationException
     *  if the given input stream cannot be read for any reason
     */
    void load(InputStream istream, Charset encoding) throws ConfigurationException;

    /**
     * Reads the configuration from the given reader, clearing any currently loaded or stored
     * configuration.
     * <p></p>
     * <strong>Note:</strong> this method does not enforce any specific character encoding, and will
     * use whichever encoding is imparted by the reader itself.
     *
     * @param reader
     *  a reader from which to read configuration
     *
     * @throws IllegalArgumentException
     *  if reader is null
     *
     * @throws ConfigurationException
     *  if the given reader cannot be read for any reason
     */
    void load(Reader reader) throws ConfigurationException;
}
