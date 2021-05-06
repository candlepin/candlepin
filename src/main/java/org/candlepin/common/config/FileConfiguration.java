/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.common.config;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;

/**
 * Classes implementing FileConfiguration take their configuration from a file source and can
 * therefore specify an encoding.
 */
public interface FileConfiguration extends Configuration {
    Charset getEncoding();
    void setEncoding(Charset encoding);

    void load(String fileName) throws ConfigurationException;
    void load(File file) throws ConfigurationException;
    void load(InputStream inStream) throws ConfigurationException;
    void load(Reader reader) throws ConfigurationException;
}
