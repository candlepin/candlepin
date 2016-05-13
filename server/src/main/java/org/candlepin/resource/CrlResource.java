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
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.X509CRLEntryWrapper;
import org.candlepin.util.CrlFileUtil;

import com.google.inject.Inject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.CRLException;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;



/**
 * CrlResource
 */
@Path("/crl")
@Api("crl")
public class CrlResource {

    private Configuration config;
    private CrlFileUtil crlFileUtil;
    private PKIUtility pkiUtility;
    private CertificateSerialCurator certificateSerialCurator;


    @Inject
    public CrlResource(Configuration config, CrlFileUtil crlFileUtil, PKIUtility pkiUtility,
        CertificateSerialCurator certificateSerialCurator) {

        this.config = config;
        this.crlFileUtil = crlFileUtil;
        this.pkiUtility = pkiUtility;
        this.certificateSerialCurator = certificateSerialCurator;
    }

    @ApiOperation(notes = "Retrieves the Certificate Revocation List", value = "getCurrentCrl")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response getCurrentCrl(@Context Principal principal) throws CRLException {
        String filePath = getCrlFilePath();
        File crlFile = new File(filePath);

        try {
            this.crlFileUtil.syncCRLWithDB(crlFile);

            // Create an empty CRL if we didn't have anything to write
            if (!crlFile.exists() || crlFile.length() < 1) {
                pkiUtility.writePemEncoded(
                    pkiUtility.createX509CRL(new LinkedList<X509CRLEntryWrapper>(), BigInteger.ZERO),
                    new FileOutputStream(crlFile)
                );
            }

            return Response.ok().entity(new FileInputStream(crlFile)).build();
        }
        catch (IOException e) {
            throw new IseException(e.getMessage(), e);
        }
    }

    @ApiOperation(notes = "Deletes a Certificate from the Revocation List", value = "unrevoke")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public void unrevoke(@QueryParam("serial") String[] serialIds)
        throws CRLException, IOException {

        String filePath = getCrlFilePath();
        File crlFile = new File(filePath);

        try {
            List<BigInteger> serials = new LinkedList<BigInteger>();
            for (CertificateSerial serial : certificateSerialCurator.listBySerialIds(serialIds)) {
                serials.add(serial.getSerial());
            }

            if (serials.size() > 0) {
                this.crlFileUtil.updateCRLFile(crlFile, null, serials);
            }
        }
        catch (IOException e) {
            throw new IseException(e.getMessage(), e);
        }
    }

    private String getCrlFilePath() {
        String filePath = config.getString(ConfigProperties.CRL_FILE_PATH);

        if (filePath == null) {
            throw new IseException("CRL file path not defined in config file");
        }

        return filePath;
    }
}
