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
package org.candlepin.resource;

import org.candlepin.auth.Principal;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.CrlGenerator;
import org.candlepin.exceptions.IseException;
import org.candlepin.pki.PKIUtility;
import org.candlepin.util.CrlFileUtil;

import com.google.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.X509CRL;
import java.util.UUID;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

/**
 * CrlResource
 */
@Path("/crl")
public class CrlResource {

    private CrlGenerator crlGenerator;
    private PKIUtility pkiUtility;
    private CrlFileUtil crlFileUtil;
    private Config config;

    @Inject
    public CrlResource(CrlGenerator crlGenerator, PKIUtility pkiUtility,
        CrlFileUtil crlFileUtil, Config config) {
        this.crlGenerator = crlGenerator;
        this.pkiUtility = pkiUtility;
        this.crlFileUtil = crlFileUtil;
        this.config = config;
    }

    /**
     * @return the current CRL
     * @throws CRLException if there is issue generating the CRL
     * @throws IOException if there is a problem serializing the CRL
     * @httpcode 200
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    public String getCurrentCrl(@Context Principal principal)
        throws CRLException, IOException {

        X509CRL crl = this.crlGenerator.createCRL();

        String filePath = config.getString(ConfigProperties.CRL_FILE_PATH);
        if (filePath == null) {
            throw new IseException("CRL file path not defined in config file");
        }
        File crlFile = new File(filePath);

        try {
            crlFileUtil.updateCRLFile(crlFile, "CN=test, UID=" + UUID.randomUUID());
        }
        catch (CertificateException e) {
            throw new IseException(e.getMessage(), e);
        }

        return new String(pkiUtility.getPemEncoded(crl));
    }
}
