/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.functional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Class to hold values used to build connections using X509 client authentication
 */
public class X509ClientProperties {
    private boolean insecure;
    private String keystorePassword;
    private String keystoreFile;
    private InputStream keystoreStream;
    private String truststoreFile;
    private String truststorePassword;
    private InputStream truststoreStream;

    public boolean isInsecure() {
        return insecure;
    }

    public void setInsecure(boolean insecure) {
        this.insecure = insecure;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public String getTruststorePassword() {
        return truststorePassword;
    }

    public void setTruststorePassword(String truststorePassword) {
        this.truststorePassword = truststorePassword;
    }

    public String getKeystoreFile() {
        return keystoreFile;
    }

    public void setKeystoreFile(String keystoreFile) {
        if (keystoreStream != null) {
            throw new IllegalStateException("A keystore has already been set as an InputStream");
        }
        this.keystoreFile = keystoreFile;
    }

    public String getTruststoreFile() {
        return truststoreFile;
    }

    public void setTruststoreFile(String truststoreFile) {
        if (truststoreStream != null) {
            throw new IllegalStateException("A truststore has already been set as an InputStream");
        }
        this.truststoreFile = truststoreFile;
    }

    public void setKeystoreStream(InputStream keystoreStream) {
        if (keystoreFile != null) {
            throw new IllegalStateException("A keystore has already been set as a file location");
        }
        this.keystoreStream = keystoreStream;
    }

    public InputStream getKeystoreStream() throws IOException {
        if (keystoreFile == null && keystoreStream == null) {
            throw new IllegalStateException("No keystore has been set");
        }

        if (keystoreFile != null && keystoreStream != null) {
            throw new IllegalStateException("Both a keystore file and InputStream are set. This is " +
                "ambiguous.");
        }

        if (keystoreStream != null) {
            return keystoreStream;
        }
        return readStream(keystoreFile);
    }

    public void setTruststoreStream(InputStream truststoreStream) {
        if (truststoreFile != null) {
            throw new IllegalStateException("A truststore has already been set as a file location");
        }
        this.truststoreStream = truststoreStream;
    }

    public InputStream getTruststoreStream() throws IOException {
        if (truststoreFile == null && truststoreStream == null) {
            throw new IllegalStateException("No truststore has been set");
        }

        if (truststoreFile != null && truststoreStream != null) {
            throw new IllegalStateException("Both a truststore file and InputStream are set. This is " +
                "ambiguous.");
        }

        if (truststoreStream != null) {
            return truststoreStream;
        }
        return readStream(truststoreFile);
    }

    private InputStream readStream(String path) throws IOException {
        return new ByteArrayInputStream(Files.readAllBytes(Paths.get(path)));
    }

    public boolean usesClientAuth() {
        return (getKeystoreFile() != null && getKeystorePassword() != null);
    }

    public boolean providesTruststore() {
        return (getTruststoreFile() != null && getTruststorePassword() != null);
    }

    public String toString() {
        return String.format("X509ClientProperties[truststore=%s, keystore=%s]",
            truststoreFile, keystoreFile);
    }
}
