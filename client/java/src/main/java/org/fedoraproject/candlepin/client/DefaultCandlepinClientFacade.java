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
public class DefaultCandlepinClientFacade implements CandlepinClientFacade {


    private Configuration config;
    static final Logger L = LoggerFactory
        .getLogger(DefaultCandlepinClientFacade.class);

    public DefaultCandlepinClientFacade(Configuration config) {
        this.config = config;
        RegisterBuiltin.register(ResteasyProviderFactory.getInstance());
    }

    /* (non-Javadoc)
     * @see org.fedoraproject.candlepin.client.CandlepinClientFacade#isRegistered()
     */
    public boolean isRegistered() {
        return getUUID() != null;
    }

    /* (non-Javadoc)
     * @see org.fedoraproject.candlepin.client.CandlepinClientFacade#getUUID()
     */
    public String getUUID() {
        String uuid = null;
        File file = new File(config.getConsumerIdentityFilePath());
        if (file.exists()) {
            String[] keyAndCert = FileUtil.readKeyAndCert(this.config
                .getConsumerIdentityFilePath());
            uuid = PemUtil.extractUUID(PemUtil.createCert(keyAndCert[1]));
        }
        return uuid;
    }

    /*
     * (non-Javadoc)
     * @see
     * org.fedoraproject.candlepin.client.CandlepinClientFacade#register(java
     * .lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public String register(String username, String password, String name,
        String type) {
        L.debug("Trying to register consumer with user:{} pass:{}",
            username, password);
        CandlepinConsumerClient client = this.clientWithCredentials(username,
            password);
        Consumer cons = new Consumer();
        cons.setName(name);
        cons.setType(type);
        cons = client.register(cons);
        recordIdentity(cons);
        return cons.getUuid();
    }

    /*
     * (non-Javadoc)
     * @see
     * org.fedoraproject.candlepin.client.CandlepinClientFacade#registerExisting
     * (java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean registerExisting(String username, String password, String uuid) {
        CandlepinConsumerClient client = this.clientWithCredentials(username,
            password);
        ClientResponse<Consumer> response = client.getConsumer(uuid);
        if (!response.getResponseStatus().getFamily().equals(
            Response.Status.Family.SUCCESSFUL)) {
            throw new ClientException(response.getResponseStatus().toString());
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * @seeorg.fedoraproject.candlepin.client.CandlepinClientFacade#
     * registerExistingCustomerWithId(java.lang.String)
     */
    public void registerExistingCustomerWithId(String uuid) {
        L.debug("Trying to register existing customer.uuid={}", uuid);
        HttpClient httpclient = new HttpClient();
        httpclient.getParams().setAuthenticationPreemptive(true);
        CandlepinConsumerClient client = ProxyFactory.create(
            CandlepinConsumerClient.class, this.config.getServerURL(),
            new ApacheHttpClientExecutor(httpclient));
        getSafeResult(client.getConsumer(uuid));
    }

    /* (non-Javadoc)
     * @see org.fedoraproject.candlepin.client.CandlepinClientFacade#unRegister()
     */
    public void unRegister() {
        CandlepinConsumerClient client = clientWithCert();
        if (isRegistered()) {
            getSafeResult(client.deleteConsumer(getUUID()));
            removeFiles();
        }
    }

    /* (non-Javadoc)
     * @see org.fedoraproject.candlepin.client.CandlepinClientFacade#listPools()
     */
    public List<Pool> listPools() {
        CandlepinConsumerClient client = clientWithCert();
        return getSafeResult(client.listPools(getUUID()));
    }

    /*
     * (non-Javadoc)
     * @see
     * org.fedoraproject.candlepin.client.CandlepinClientFacade#bindByPool(java
     * .lang.Long, int)
     */
    public List<Entitlement> bindByPool(Long poolId, int quantity) {
        L.debug("bindByPool(poolId={})", poolId);
        CandlepinConsumerClient client = clientWithCert();
        return getSafeResult(client
            .bindByEntitlementID(getUUID(), poolId, quantity));
    }

    /*
     * (non-Javadoc)
     * @see
     * org.fedoraproject.candlepin.client.CandlepinClientFacade#bindByProductId
     * (java.lang.String, int)
     */
    public List<Entitlement> bindByProductId(String productId, int quantity) {
        L.debug("bindByProductId(productId={})", productId);
        CandlepinConsumerClient client = clientWithCert();
        return getSafeResult(client.bindByProductId(
            getUUID(), productId, quantity));
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


    /*
     * (non-Javadoc)
     * @see
     * org.fedoraproject.candlepin.client.CandlepinClientFacade#bindByRegNumber
     * (java.lang.String, int)
     */
    public List<Entitlement> bindByRegNumber(String regNo, int quantity) {
        L.debug("bindByRegNumber(regNo={})", regNo);
        CandlepinConsumerClient client = clientWithCert();
        return getSafeResult(client.bindByRegNumber(
            getUUID(), regNo, quantity));
    }

    /*
     * (non-Javadoc)
     * @see
     * org.fedoraproject.candlepin.client.CandlepinClientFacade#bindByRegNumber
     * (java.lang.String, int, java.lang.String, java.lang.String)
     */
    public List<Entitlement> bindByRegNumber(String regNo, int quantity,
        String emailId, String defLang) {
        L.debug("bindByRegNumber(regNo={}, quantity={}, emailId, defLang)",
            regNo, quantity);
        L.debug("emailId={}, defLang={}", emailId, defLang);
        CandlepinConsumerClient client = clientWithCert();
        return getSafeResult(client.bindByRegNumber(getUUID(), regNo, quantity,
            emailId, defLang));
    }

    /*
     * (non-Javadoc)
     * @see
     * org.fedoraproject.candlepin.client.CandlepinClientFacade#unBindBySerialNumber
     * (int)
     */
    public void unBindBySerialNumber(int serialNumber) {
        L.debug("unBindBySerialNumber(serialNumber={})", serialNumber);
        CandlepinConsumerClient client = clientWithCert();
        getSafeResult(client.unBindBySerialNumber(getUUID(),
            serialNumber));
    }

    /* (non-Javadoc)
     * @see org.fedoraproject.candlepin.client.CandlepinClientFacade#unBindAll()
     */
    public void unBindAll() {
        L.debug("Unbinding all for customer {}", getUUID());
        CandlepinConsumerClient client = clientWithCert();
        getSafeResult(client.unBindAll(getUUID()));
    }

    /*
     * (non-Javadoc)
     * @seeorg.fedoraproject.candlepin.client.CandlepinClientFacade#
     * updateEntitlementCertificates()
     */
    public boolean updateEntitlementCertificates() {
        L.debug("updating current entitlement certificates of the customer {}", getUUID());
        File entitlementDir = new File(config.getEntitlementDirPath());
        if (entitlementDir.exists() && entitlementDir.isDirectory()) {
            L.debug("Removing files : {}", Arrays.toString(entitlementDir.list()));
            FileUtil.removeFiles(entitlementDir.listFiles());
            L.debug("Successfully removed files inside directory: {}", entitlementDir);
        }
        FileUtil.mkdir(config.getEntitlementDirPath());
        CandlepinConsumerClient client = clientWithCert();
        List<EntitlementCertificate> certs = getSafeResult(client
            .getEntitlementCertificates(getUUID()));
        L.info("Retrieved #{} entitlement certificates", certs.size());

        for (EntitlementCertificate cert : certs) {
            String fileName = config.getEntitlementDirPath() + File.separator +
                cert.getSerial() + ".pem";
            FileUtil.dumpKeyAndCert(cert.getKey(), cert
                .getX509CertificateAsPem(), fileName);
            L.debug("Wrote key: {}, cert: {}",
                cert.getKey(), cert.getX509CertificateAsPem());
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * @seeorg.fedoraproject.candlepin.client.CandlepinClientFacade#
     * getCurrentEntitlementCertificates()
     */
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
                    return name.matches("(\\d)+.pem");
                }
            });
        L.debug("Number of entitlement certificates = #{}",
            entitlementDir.list().length);

        List<EntitlementCertificate> certs = Utils.newList();
        for (File file : entitlementDirs) {
            String [] keyCert = FileUtil.readKeyAndCert(file.getAbsolutePath());
            EntitlementCertificate entCert = new EntitlementCertificate(PemUtil
                .createCert(keyCert[1]), PemUtil
                .readPrivateKeyFromStr(keyCert[0]));
            certs.add(entCert);
            L.debug("Read entitlement & key certificate: {}",
                file);
        }
        return certs;
    }

    /*
     * (non-Javadoc)
     * @seeorg.fedoraproject.candlepin.client.CandlepinClientFacade#
     * getInstalledProductCertificates()
     */
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

    protected CandlepinConsumerClient clientWithCert() {
        try {
            HttpClient httpclient = new HttpClient();
            CustomSSLProtocolSocketFactory factory  =
                new CustomSSLProtocolSocketFactory(config);
            URL hostUrl = new URL(config.getServerURL());
            Protocol customHttps = new Protocol("https", factory, 8443);
            Protocol.registerProtocol("https", customHttps);
            httpclient.getHostConfiguration().setHost(hostUrl.getHost(),
                hostUrl.getPort(), customHttps);
            httpclient.getParams().setConnectionManagerTimeout(1000);
            CandlepinConsumerClient client = ProxyFactory.create(
                CandlepinConsumerClient.class, config.getServerURL(),
                new ApacheHttpClientExecutor(httpclient));
            return client;
        }
        catch (Exception e) {
            throw new ClientException(e);
        }
    }

    protected CandlepinConsumerClient clientWithCredentials(String username,
        String password) {
        HttpClient httpclient = new HttpClient();
        httpclient.getParams().setAuthenticationPreemptive(true);
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(
            username, password);
        httpclient.getState().setCredentials(AuthScope.ANY, creds);
        CandlepinConsumerClient client = ProxyFactory.create(
            CandlepinConsumerClient.class, config.getServerURL(),
            new ApacheHttpClientExecutor(httpclient));
        return client;
    }

    protected void recordIdentity(Consumer cons) {
        L.debug("Recording identity of consumer: {}", cons);
        FileUtil.mkdir(config.getConsumerDirPath());

        L.debug("Dumping key & certificate to file: {}",
            config.getConsumerIdentityFilePath());

        FileUtil.dumpKeyAndCert(cons.getIdCert().getKey(), cons.getIdCert().getCert(),
            config.getConsumerIdentityFilePath());
    }

    protected void removeFiles() {
        L.debug("Removing file: {}", config.getConsumerIdentityFilePath());
        if (!new File(config.getConsumerIdentityFilePath()).delete()) {
            L.warn("Failed to remove identity file: {} ", config
                .getConsumerIdentityFilePath());
        }
    }

}
