/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.pinsetter.tasks;

import static org.fedoraproject.candlepin.util.Util.getValue;
import static org.fedoraproject.candlepin.util.Util.newList;
import static org.fedoraproject.candlepin.util.Util.newMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.security.auth.x500.X500Principal;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.x509.X509V2CRLGenerator;
import org.bouncycastle.x509.extension.AuthorityKeyIdentifierStructure;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.SystemPrincipal;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.model.CertificateSerial;
import org.fedoraproject.candlepin.model.CertificateSerialCurator;
import org.fedoraproject.candlepin.pki.PKIReader;
import org.fedoraproject.candlepin.util.Util;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * CertificateRevocationListTask.
 */
public class CertificateRevocationListTask implements Job {
    
    //"*/1 * * * * ?";
    /** The Constant DEFAULT_SCHEDULE. */
    public static final String DEFAULT_SCHEDULE = "* * */24 * * ?";
    
    /** The pki reader. */
    private PKIReader pkiReader;
    
    /** The config. */
    private Config config;
    
    /** The certificate serial curator. */
    private CertificateSerialCurator certificateSerialCurator;
    
    /** The algorithm. */
    private String algorithm;

    private static Logger log = Logger.getLogger(CertificateRevocationListTask.class); 
    /**
     * Instantiates a new certificate revocation list task.
     * 
     * @param rdr the rdr
     * @param conf the conf
     * @param curator the curator
     * @param algorithm the algorithm
     */
    @Inject
    public CertificateRevocationListTask(PKIReader rdr, Config conf,
        CertificateSerialCurator curator, @Named("crlSignatureAlgo")String algorithm) {
        this.pkiReader = rdr;
        this.config = conf;
        this.certificateSerialCurator = curator;
        this.algorithm = algorithm;
    }

    /* (non-Javadoc)
     * @see org.quartz.Job#execute(org.quartz.JobExecutionContext)
     */
    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        String filePath = config.getString(ConfigProperties.CRL_FILE_PATH);
        log.info("Executing CRL Job. CRL filePath=" + filePath);
        File crlFile = new File(filePath);
        Principal systemPrincipal = new SystemPrincipal();
        ResteasyProviderFactory.pushContext(Principal.class, systemPrincipal);
        this.updateCRL(crlFile, "CN=test, UID=" + UUID.randomUUID());
        ResteasyProviderFactory.popContextData(Principal.class);
    }

    /**
     * The Class SimpleCRLEntry.
     */
    protected static class SimpleCRLEntry{
        
        /** The serial number. */
        protected BigInteger serialNumber;
        
        /** The revocation date. */
        protected Date revocationDate;

        /**
         * Instantiates a new simple crl entry.
         * 
         * @param serialNumber the serial number
         * @param revocationDate the revocation date
         */
        private SimpleCRLEntry(BigInteger serialNumber, Date revocationDate) {
            this.serialNumber = serialNumber;
            this.revocationDate = revocationDate;
        }
    }

    /**
     * Generate crl.
     * 
     * @param entries the entries
     * @param principal the principal
     * @param crlNumber the crl number
     * @return the x509 crl
     */
    protected X509CRL generateCRL(Iterator<SimpleCRLEntry> entries,
        String principal, BigInteger crlNumber) {
        try {
            X509V2CRLGenerator generator = new X509V2CRLGenerator();
            generator.setIssuerDN(new X500Principal(principal));
            generator.setThisUpdate(new Date());
            generator.setNextUpdate(Util.tomorrow());
            generator.setSignatureAlgorithm(algorithm);
            //add all the crl entries.
            while (entries.hasNext()) {
                SimpleCRLEntry entry = entries.next();
                generator.addCRLEntry(entry.serialNumber, entry.revocationDate,
                    CRLReason.privilegeWithdrawn);
            }
            log.info("Completed adding CRL numbers to the certificate.");
            generator.addExtension(X509Extensions.AuthorityKeyIdentifier,
                false, new AuthorityKeyIdentifierStructure(pkiReader.getCACert()));
            generator.addExtension(X509Extensions.CRLNumber, false,
                new CRLNumber(crlNumber));
            return generator.generate(pkiReader.getCaKey());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the new serials to append and set them consumed.
     * 
     * @return the new serials to append and set them consumed
     */
    protected List<SimpleCRLEntry> getNewSerialsToAppendAndSetThemConsumed() {
        List<SimpleCRLEntry> entries = Util.newList();
        List<CertificateSerial> serials =  
            this.certificateSerialCurator.retrieveTobeCollectedSerials();
        for (CertificateSerial cs : serials) {
            entries.add(new SimpleCRLEntry(cs.getSerial(),
                    cs.getExpiration()));
            cs.setCollected(true);
        }
        if (log.isTraceEnabled()) {
            log.trace("Added #" + serials.size() + " new entries to the CRL");
        }
        if (log.isTraceEnabled()) {
            StringBuilder builder = new StringBuilder("[ ");
            for (CertificateSerial cs : serials) {
                builder.append(cs.getSerial()).append(", ");
            }
            builder.append(" ]");
            log.trace("Newly added serials = " + builder.toString());
        }
        this.certificateSerialCurator.saveOrUpdateAll(serials);
        return entries;
    }

    /**
     * Removes the expired serials.
     * 
     * @param revokedEntries the revoked entries
     * @return the set
     */
    protected Set<? extends X509CRLEntry> removeExpiredSerials(
        Set<? extends X509CRLEntry> revokedEntries) {
        if (revokedEntries == null || revokedEntries.size() == 0) {
            return Util.newSet();
        }
        Map<BigInteger, X509CRLEntry> map = newMap();
        for (X509CRLEntry entry : revokedEntries) {
            map.put(entry.getSerialNumber(), entry);
        }
        for (CertificateSerial cs : this.certificateSerialCurator
            .getExpiredSerials()) {
            X509CRLEntry entry = map.get(cs.getSerial());
            if (entry != null) {
                revokedEntries.remove(entry);
                if (log.isTraceEnabled()) {
                    log.trace("Serial #" + cs.getId() +
                        " has expired. Removing it from CRL");
                }
            }
        }
        return revokedEntries;
    }

    /**
     * Update crl.
     * 
     * @param x509crl the x509crl
     * @param principal the principal
     * @return the x509 crl
     */
    protected X509CRL updateCRL(X509CRL x509crl, String principal) {
        BigInteger no = getCRLNumber(x509crl);
        log.info("Old CRLNumber is : " + no);
        List<SimpleCRLEntry> crlEntries = newList();
        if (x509crl != null) {
            crlEntries = this.toSimpleCRLEntries(removeExpiredSerials(x509crl
                .getRevokedCertificates()));
        }
        crlEntries.addAll(getNewSerialsToAppendAndSetThemConsumed());
        this.certificateSerialCurator.deleteExpiredSerials();
        return this.generateCRL(crlEntries.iterator(), principal, no
            .add(BigInteger.ONE));
    }

    /**
     * Gets the cRL number.
     * 
     * @param x509crl the x509crl
     * @return the cRL number
     */
    protected BigInteger getCRLNumber(X509CRL x509crl) {
        if (x509crl == null) {
            return BigInteger.ZERO; 
        }
        return new BigInteger(getValue(x509crl, "2.5.29.20"));
    }
    
    /**
     * To simple crl entries.
     * 
     * @param entries the entries
     * @return the list
     */
    protected List<SimpleCRLEntry> toSimpleCRLEntries(
        Set<? extends X509CRLEntry> entries) {
        List<SimpleCRLEntry> crlEntries = newList();
        for (X509CRLEntry entry : entries) {
            crlEntries.add(new SimpleCRLEntry(entry.getSerialNumber(), entry
                .getRevocationDate()));
        }
        return crlEntries;
    }
    
    /**
     * Update crl.
     * 
     * @param in the in
     * @param principal the principal
     * @param out the out
     */
    public void updateCRL(InputStream in, String principal, OutputStream out) {
        try {
            X509CRL x509crl = null;
            if (in != null) {
                x509crl = (X509CRL) CertificateFactory.getInstance("X.509")
                    .generateCRL(in);
            }
            x509crl = updateCRL(x509crl, principal);
            PEMWriter writer = new PEMWriter(new OutputStreamWriter(out));
            writer.writeObject(x509crl);
            writer.flush();
            writer.close();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Update crl.
     * 
     * @param file the file
     * @param principal the principal
     */
    public void updateCRL(File file, String principal) {
        FileInputStream in = null;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            if (file.exists() && file.length() > 0) {
                log.info("CRL File: " + file + " exists. Loading the old CRL");
                in = new FileInputStream(file);
            }
            else {
                log.info("CRL File: " + file + " either does not exist");
            }
            updateCRL(in, principal, stream);
            log.info("Completed generating CRL. Writing it to disk");
            FileUtils.writeByteArrayToFile(file, stream.toByteArray());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
    
}
