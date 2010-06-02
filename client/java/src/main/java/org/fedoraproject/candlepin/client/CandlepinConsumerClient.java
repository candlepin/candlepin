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
package org.fedoraproject.candlepin.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.net.URL;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.fedoraproject.candlepin.client.cmds.Utils;
import org.fedoraproject.candlepin.client.model.Consumer;
import org.fedoraproject.candlepin.client.model.Entitlement;
import org.fedoraproject.candlepin.client.model.EntitlementCertificate;
import org.fedoraproject.candlepin.client.model.Pool;
import org.fedoraproject.candlepin.client.model.ProductCertificate;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.client.ProxyFactory;
import org.jboss.resteasy.client.core.executors.ApacheHttpClientExecutor;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.util.GenericType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * CandlepinConsumerClient
 */
public class CandlepinConsumerClient {

 
    private Configuration config;
    static final Logger L = LoggerFactory
        .getLogger(CandlepinConsumerClient.class);
    
    public CandlepinConsumerClient(Configuration config) {
        this.config = config;
        RegisterBuiltin.register(ResteasyProviderFactory.getInstance());
    }

    /**
     * Returns true if the client is already registered
     * @return true if registered
     */
    public boolean isRegistered() {
        return getUUID() != null;
    }

    /**
     * Returns the UUID for the consumer, or null if not registered.
     * @return the UUID of the consumer
     */
    public String getUUID() {
        String uuid = null;
        File certFile = new File(this.config.getCertificateFilePath());
        if (certFile.exists()) {
            uuid = PemUtil.extractUUID(this.config.getCertificateFilePath());
        }
        return uuid;
    }

    /**
     * Registers a consumer with a provided name and type. The credentials are
     * user for authentication.
     * @return The UUID of the new consumer.
     */
    public String register(String username, String password, String name,
        String type) {
        L.debug("Trying to register consumer with user:{} pass:{}",
            username, password);
        ICandlepinConsumerClient client = this.clientWithCredentials(username,
            password);
        Consumer cons = new Consumer();
        cons.setName(name);
        cons.setType(type);
        cons = client.register(cons);
        recordIdentity(cons);
        return cons.getUuid();
    }

    /**
     * Register to an existing consumer. The credentials are user for
     * authentication.
     * 
     * @param username the username
     * @param password the password
     * @param uuid the uuid
     * @return true, if successful
     */
    public boolean registerExisting(String username, String password, String uuid) {
        ICandlepinConsumerClient client = this.clientWithCredentials(username,
            password);
        ClientResponse<Consumer> response = client.getConsumer(uuid);
        if (!response.getResponseStatus().getFamily().equals(
            Response.Status.Family.SUCCESSFUL)) {
            throw new ClientException(response.getResponseStatus().toString());
        }
        return true;
    }

    /**
     * Register an existing consumer based on the uuid.
     * 
     * @param uuid - the consumer id
     */
    public void registerExistingCustomerWithId(String uuid) {
        L.debug("Trying to register existing customer.uuid={}", uuid);
        HttpClient httpclient = new HttpClient();
        httpclient.getParams().setAuthenticationPreemptive(true);
        ICandlepinConsumerClient client = ProxyFactory.create(
            ICandlepinConsumerClient.class, this.config.getServerURL(),
            new ApacheHttpClientExecutor(httpclient));
        getSafeResult(client.getConsumer(uuid));
    }

    /**
     * Remove he consumer from candlepin and all of the entitlements which the
     * conumser have subscribed to.
     * 
     */
    public void unRegister() {
        ICandlepinConsumerClient client = clientWithCert();
        if (isRegistered()) {
            getSafeResult(client.deleteConsumer(getUUID()));
            removeFiles();
        }
    }

    /**
     * List the pools which the consumer could subscribe to
     * @return the list of exception
     */
    public List<Pool> listPools() {
        ICandlepinConsumerClient client = clientWithCert();
        return getSafeResult(client.listPools(getUUID()));
    }
    
    public List<Entitlement> bindByPool(Long poolId) {
        L.debug("bindByPool(poolId={})", poolId);
        ICandlepinConsumerClient client = clientWithCert();
        return getSafeResult(client
            .bindByEntitlementID(getUUID(), poolId));
    }
    
    public List<Entitlement> bindByProductId(String productId) {
        L.debug("bindByProductId(productId={})", productId);
        ICandlepinConsumerClient client = clientWithCert();
        return getSafeResult(client.bindByProductId(
            getUUID(), productId));
    }
    
    /**
     * Gets the safe result.
     * @param <T> the generic type
     * @param response the response
     * @return the safe result
     */
    private <T> T getSafeResult(ClientResponse<T> response) {
        switch (response.getResponseStatus().getFamily()) {
            case CLIENT_ERROR:
                Map<String, String> msg = response
                    .getEntity(new GenericType<Map<String, String>>() {
                    });
                L.warn("Operation failure. Status = {}. Response from server: {}",
                    ReflectionToStringBuilder.reflectionToString(response
                        .getResponseStatus()), Utils.toStr(msg));
                throw new ClientException(response.getResponseStatus(), msg
                    .get(Constants.ERR_DISPLAY_MSG));
            default:
                return response.getEntity();
        }
    }


    public List<Entitlement> bindByRegNumber(String regNo) {
        L.debug("bindByRegNumber(regNo={})", regNo);
        ICandlepinConsumerClient client = clientWithCert();
        return getSafeResult(client.bindByRegNumber(
            getUUID(), regNo));
    }
    
    public void unBindBySerialNumber(int serialNumber) {
        L.debug("unBindBySerialNumber(serialNumber={})", serialNumber);
        ICandlepinConsumerClient client = clientWithCert();
        getSafeResult(client.unBindBySerialNumber(getUUID(),
            serialNumber));
    }

    public void unBindAll() {
        L.debug("Unbinding all for customer {}", getUUID());
        ICandlepinConsumerClient client = clientWithCert();
        getSafeResult(client.unBindAll(getUUID()));
    }

    public boolean updateEntitlementCertificates() {
        L.debug("updating current entitlement certificates of the customer {}", getUUID());
        File entitlementDir = new File(config.getEntitlementDirPath());
        if (entitlementDir.exists() && entitlementDir.isDirectory()) {
            L.debug("Removing files : {}", Arrays.toString(entitlementDir.list()));
            FileUtil.removeFiles(entitlementDir.listFiles());
            L.debug("Successfully removed files inside directory: {}", entitlementDir);
        }
        FileUtil.mkdir(config.getEntitlementDirPath());
        ICandlepinConsumerClient client = clientWithCert();
        List<EntitlementCertificate> certs = getSafeResult(client
            .getEntitlementCertificates(getUUID()));
        L.info("Retrieved #{} entitlement certificates", certs.size());
        
        for (EntitlementCertificate cert : certs) {
            String entCertFileName = config.getEntitlementDirPath() +
                File.separator + cert.getSerial() + "-cert.pem";
            String entKeyFileName = config.getEntitlementDirPath() +
                File.separator + cert.getSerial() + "-key.pem";
            L.debug("Writing to file: {} data: {}", entCertFileName,
                cert.getX509CertificateAsPem());
            FileUtil.dumpToFile(entCertFileName, cert.getX509CertificateAsPem());
            L.debug("Writing to file: {} data: {}", entKeyFileName,
                cert.getKey());
            FileUtil.dumpToFile(entKeyFileName, cert.getKey());
        }
        return true;
    }

    public List<EntitlementCertificate> getCurrentEntitlementCertificates() {
        File entitlementDir = new File(config.getEntitlementDirPath());
        if (!entitlementDir.isDirectory() || !entitlementDir.canRead()) {
            L.info("Directory: {} could not be read. Returning empty list",
                entitlementDir.getAbsolutePath());
            return Collections.emptyList();
        }
        
        File[] entitlementDirs = entitlementDir
            .listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith("-cert.pem");
                }
            });
        L.debug("Number of entitlement certificates = #{}",
            entitlementDir.length());
        
        List<EntitlementCertificate> certs = Utils.newList();
        for (File file : entitlementDirs) {
            String filename = file.getAbsolutePath();
            String eKeyFileName = filename.replace("-cert.pem", "-key.pem");
            X509Certificate cert = PemUtil.readCert(filename);
            PrivateKey key = PemUtil.readPrivateKey(eKeyFileName);
            EntitlementCertificate entCert = new EntitlementCertificate(cert,
                key);
            certs.add(entCert);
            L.debug("Read entitlement & key certificate: {}",
                filename, eKeyFileName);
        }
        return certs;
    }

    public List<ProductCertificate> getInstalledProductCertificates() {
        File file = new File(this.config.getProductDirPath());
        L.info("Trying to read product certificates from dir: {}", file);
        if (file.exists() && file.isDirectory()) {
            File[] prodCerts = file.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".pem");
                }
            });
            L.debug("Number of product certificates = #{}", prodCerts.length);
            if (prodCerts.length == 0) {
                return Collections.emptyList();
            }
            List<ProductCertificate> productCertificates = Utils.newList();
            for (File certificate : prodCerts) {
                ProductCertificate pc = new ProductCertificate(PemUtil
                    .readCert(certificate.getAbsolutePath()));
                productCertificates.add(pc);
                L.debug("Read product certificate: {}", certificate);
            }
            return productCertificates;
        }
        else {
            L.info("Product certificates directory: {} could not be read.", file);
            return Collections.emptyList();
        }
    }

    public void generatePKCS12Certificates(String password) {
        try {
            List<EntitlementCertificate> certs = getCurrentEntitlementCertificates();
            for (EntitlementCertificate cert : certs) {
                KeyStore store = PKCS12Util.createPKCS12Keystore(cert
                    .getX509Certificate(), cert.getPrivateKey(), null);
                File p12File = new File(config.getEntitlementDirPath() + File.separator +
                    cert.getSerial() + ".p12");
                store.store(new FileOutputStream(p12File), password
                    .toCharArray());
            }
        }
        catch (Exception e) {
            throw new ClientException(e);
        }
    }
    
    protected ICandlepinConsumerClient clientWithCert() {
        try {
            HttpClient httpclient = new HttpClient();
            CustomSSLProtocolSocketFactory factory  =
                new CustomSSLProtocolSocketFactory(
                    config.getCertificateFilePath(), config.getKeyFilePath(), config);
            URL hostUrl = new URL(config.getServerURL());
            Protocol customHttps = new Protocol("https", factory, 8443);
            Protocol.registerProtocol("https", customHttps);
            httpclient.getHostConfiguration().setHost(hostUrl.getHost(),
                hostUrl.getPort(), customHttps);
            httpclient.getParams().setConnectionManagerTimeout(1000);
            ICandlepinConsumerClient client = ProxyFactory.create(
                ICandlepinConsumerClient.class, config.getServerURL(),
                new ApacheHttpClientExecutor(httpclient));
            return client;
        }
        catch (Exception e) {
            throw new ClientException(e);
        }
    }

    protected ICandlepinConsumerClient clientWithCredentials(String username,
        String password) {
        HttpClient httpclient = new HttpClient();
        httpclient.getParams().setAuthenticationPreemptive(true);
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(
            username, password);
        httpclient.getState().setCredentials(AuthScope.ANY, creds);

        ICandlepinConsumerClient client = ProxyFactory.create(
            ICandlepinConsumerClient.class, config.getServerURL(),
            new ApacheHttpClientExecutor(httpclient));
        return client;
    }

    protected void recordIdentity(Consumer aConsumer) {
        L.debug("Recording identity of consumer: {}", aConsumer);
        FileUtil.mkdir(config.getConsumerDirPath());
        L.debug("Dumping certificate[{}] and key files[{}]",
            config.getCertificateFilePath(), config.getKeyFilePath());
        FileUtil.dumpToFile(config.getCertificateFilePath(),
            aConsumer.getIdCert().getCert());
        FileUtil.dumpToFile(config.getKeyFilePath(), aConsumer.getIdCert().getKey());
    }

    protected void removeFiles() {
        L.debug("Removing files: {} & {}", config.getCertificateFilePath(),
            config.getKeyFilePath());
        FileUtil.removeFiles(new String[]{ config.getCertificateFilePath(),
            config.getKeyFilePath() });
    }

}
