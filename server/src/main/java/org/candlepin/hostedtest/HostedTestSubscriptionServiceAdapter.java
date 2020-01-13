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
package org.candlepin.hostedtest;

import org.candlepin.model.Branding;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCertificate;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Owner;
import org.candlepin.model.ProductContent;
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.model.BrandingInfo;
import org.candlepin.service.model.CdnInfo;
import org.candlepin.service.model.CertificateInfo;
import org.candlepin.service.model.CertificateSerialInfo;
import org.candlepin.service.model.ConsumerInfo;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;



/**
 * The HostedTestSubscriptionServiceAdapter class is used to provide an
 * in-memory upstream source for subscriptions when candlepin is run in hosted
 * mode, while it is built with candlepin, it is not packaged in candlepin.war,
 * as the only purpose of this class is to support spec tests.
 */
@Singleton
public class HostedTestSubscriptionServiceAdapter implements SubscriptionServiceAdapter {
    private static Logger log = LoggerFactory.getLogger(HostedTestSubscriptionServiceAdapter.class);

    // Owner mapping. Owners are mapped using owner key to a simplified Owner object containing only
    // a minimal set of data to be a valid OwnerInfo instance (likely just the key itself)
    protected Map<String, Owner> ownerMap;

    // Subscription mapping. Subscriptions themselves are mapped by subscription ID to DTO. Since
    // we don't store owners (orgs) at this level, we just maintain a mapping of owner keys
    // (account) to subscription IDs.
    protected Map<String, Subscription> subscriptionMap;

    // At the time of writing, upstream product and content data is global; no need to separate these
    // by org. Mapped by RHID to DTO
    protected Map<String, ProductData> productMap;
    protected Map<String, ContentData> contentMap;

    // These are used to provide reverse lookups from child objects back to their parents
    protected Map<String, Set<String>> contentProductMap;
    protected Map<String, Set<String>> productSubscriptionMap;

    /**
     * Creates a new HostedTestSubscriptionServiceAdapter instance
     */
    @Inject
    public HostedTestSubscriptionServiceAdapter() {
        this.ownerMap = new ConcurrentHashMap<>();

        this.subscriptionMap = new ConcurrentHashMap();

        this.productMap = new ConcurrentHashMap();
        this.contentMap = new ConcurrentHashMap();

        this.contentProductMap = new ConcurrentHashMap();
        this.productSubscriptionMap = new ConcurrentHashMap();
    }



    public SubscriptionInfo createSubscription(SubscriptionInfo sinfo) {
        if (sinfo == null) {
            throw new IllegalArgumentException("sinfo is null");
        }

        if (StringUtils.isBlank(sinfo.getId())) {
            throw new IllegalArgumentException("subscription is lacking an identifier: " + sinfo);
        }

        if (this.subscriptionMap.containsKey(sinfo.getId())) {
            throw new IllegalStateException("subscription already exists: " + sinfo.getId());
        }

        // TODO: Add any other subscription validation we want here

        Subscription sdata = new Subscription();

        sdata.setId(sinfo.getId());
        sdata.setOwner(this.resolveOwner(sinfo.getOwner()));

        sdata.setProduct(this.resolveProduct(sinfo.getProduct()));
        sdata.setProvidedProducts(this.resolveProducts(sinfo.getProvidedProducts()));
        sdata.setDerivedProduct(this.resolveProduct(sinfo.getDerivedProduct()));
        sdata.setDerivedProvidedProducts(this.resolveProducts(sinfo.getDerivedProvidedProducts()));

        sdata.setQuantity(sinfo.getQuantity());
        sdata.setStartDate(sinfo.getStartDate());
        sdata.setEndDate(sinfo.getEndDate());
        sdata.setModified(sinfo.getLastModified());
        sdata.setContractNumber(sinfo.getContractNumber());
        sdata.setAccountNumber(sinfo.getAccountNumber());
        sdata.setOrderNumber(sinfo.getOrderNumber());

        sdata.setUpstreamPoolId(sinfo.getUpstreamPoolId());
        sdata.setUpstreamEntitlementId(sinfo.getUpstreamEntitlementId());
        sdata.setUpstreamConsumerId(sinfo.getUpstreamConsumerId());
        sdata.setCertificate(this.convertSubscriptionCertificate(sinfo.getCertificate()));
        sdata.setCdn(this.convertCdn(sinfo.getCdn()));

        // Update mappings
        this.subscriptionMap.put(sdata.getId(), sdata);
        this.updateSubscriptionProductMappings(sdata);

        return sdata;
    }


    public SubscriptionInfo updateSubscription(String subscriptionId, SubscriptionInfo sinfo) {
        if (subscriptionId == null) {
            throw new IllegalArgumentException("subscriptionId is null");
        }

        if (sinfo == null) {
            throw new IllegalArgumentException("sinfo is null");
        }

        Subscription sdata = this.subscriptionMap.get(subscriptionId);

        if (sdata == null) {
            throw new IllegalStateException("subscription does not exist: " + subscriptionId);
        }

        // Apply updates...

        // Do product resolution here
        ProductData product = this.resolveProduct(sinfo.getProduct());
        Collection<ProductData> providedProducts = this.resolveProducts(sinfo.getProvidedProducts());

        ProductData dProduct = this.resolveProduct(sinfo.getDerivedProduct());
        Collection<ProductData> dpProvidedProducts = this.resolveProducts(sinfo.getDerivedProvidedProducts());

        // If they all resolved, set the products
        if (product != null) {
            sdata.setProduct(product);
        }

        if (providedProducts != null) {
            sdata.setProvidedProducts(providedProducts);
        }

        sdata.setDerivedProduct(dProduct);

        if (dpProvidedProducts != null) {
            sdata.setDerivedProvidedProducts(dpProvidedProducts);
        }

        // Set the other "safe" properties here...
        if (sinfo.getOwner() != null) {
            sdata.setOwner(this.resolveOwner(sinfo.getOwner()));
        }

        if (sinfo.getQuantity() != null) {
            sdata.setQuantity(sinfo.getQuantity());
        }

        if (sinfo.getStartDate() != null) {
            sdata.setStartDate(sinfo.getStartDate());
        }

        if (sinfo.getEndDate() != null) {
            sdata.setEndDate(sinfo.getEndDate());
        }

        if (sinfo.getLastModified() != null) {
            sdata.setModified(sinfo.getLastModified());
        }

        if (sinfo.getContractNumber() != null) {
            sdata.setContractNumber(sinfo.getContractNumber());
        }

        if (sinfo.getAccountNumber() != null) {
            sdata.setAccountNumber(sinfo.getAccountNumber());
        }

        if (sinfo.getOrderNumber() != null) {
            sdata.setOrderNumber(sinfo.getOrderNumber());
        }

        if (sinfo.getUpstreamPoolId() != null) {
            sdata.setUpstreamPoolId(sinfo.getUpstreamPoolId());
        }

        if (sinfo.getUpstreamEntitlementId() != null) {
            sdata.setUpstreamEntitlementId(sinfo.getUpstreamEntitlementId());
        }

        if (sinfo.getUpstreamConsumerId() != null) {
            sdata.setUpstreamConsumerId(sinfo.getUpstreamConsumerId());
        }

        sdata.setCertificate(this.convertSubscriptionCertificate(sinfo.getCertificate()));

        sdata.setCdn(this.convertCdn(sinfo.getCdn()));

        // Update mappings...
        this.updateSubscriptionProductMappings(sdata);

        return sdata;
    }

    public SubscriptionInfo deleteSubscription(String subscriptionId) {
        if (subscriptionId == null) {
            throw new IllegalArgumentException("subscriptionId is null");
        }

        if (!this.subscriptionMap.containsKey(subscriptionId)) {
            throw new IllegalStateException("subscription does not exist: " + subscriptionId);
        }

        Subscription sdata = this.subscriptionMap.remove(subscriptionId);

        // Remove any product mappings to this subscription...
        for (Set<String> sids : this.productSubscriptionMap.values()) {
            sids.remove(subscriptionId);
        }

        return sdata;
    }

    public ProductInfo createProduct(ProductInfo pinfo) {
        if (pinfo == null) {
            throw new IllegalArgumentException("pinfo is null");
        }

        if (StringUtils.isBlank(pinfo.getId())) {
            throw new IllegalArgumentException("product is lacking an identifier: " + pinfo);
        }

        if (this.productMap.containsKey(pinfo.getId())) {
            throw new IllegalStateException("product already exists: " + pinfo.getId());
        }

        // TODO: Add any other product validation we want here

        ProductData pdata = new ProductData();

        pdata.setId(pinfo.getId());
        pdata.setProductContent(this.resolveProductContent(pinfo.getProductContent()));
        pdata.setName(pinfo.getName());
        pdata.setMultiplier(pinfo.getMultiplier());
        pdata.setAttributes(pinfo.getAttributes());
        pdata.setDependentProductIds(pinfo.getDependentProductIds());
        pdata.setCreated(new Date());
        pdata.setUpdated(new Date());
        pdata.setBranding(this.resolveBranding(pinfo.getBranding()));
        pdata.setProvidedProducts(this.resolveProducts(pinfo.getProvidedProducts()));

        // Create our mappings...
        this.productMap.put(pdata.getId(), pdata);
        this.productSubscriptionMap.put(pdata.getId(), new HashSet<>());

        // Update content=>product mappings
        this.updateProductContentMappings(pdata);

        return pdata;
    }

    public ProductInfo updateProduct(String productId, ProductInfo pinfo) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        if (pinfo == null) {
            throw new IllegalArgumentException("pinfo is null");
        }

        ProductData pdata = this.productMap.get(productId);

        if (pdata == null) {
            throw new IllegalStateException("product does not exist: " + productId);
        }

        // Apply updates...
        // Don't allow changing the ID

        Collection<ProductContentData> pcdata = this.resolveProductContent(pinfo.getProductContent());
        if (pcdata != null) {
            pdata.setProductContent(pcdata);
        }

        if (pinfo.getName() != null) {
            pdata.setName(pinfo.getName());
        }

        if (pinfo.getMultiplier() != null) {
            pdata.setMultiplier(pinfo.getMultiplier());
        }

        if (pinfo.getAttributes() != null) {
            pdata.setAttributes(pinfo.getAttributes());
        }

        if (pinfo.getDependentProductIds() != null) {
            pdata.setDependentProductIds(pinfo.getDependentProductIds());
        }

        if (pinfo.getBranding() != null) {
            pdata.setBranding(this.resolveBranding(pinfo.getBranding()));
        }

        // Update product=>content mappings
        this.updateProductContentMappings(pdata);

        pdata.setUpdated(new Date());

        return pdata;
    }

    public ProductInfo deleteProduct(String productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        if (!this.productMap.containsKey(productId)) {
            throw new IllegalStateException("Product does not exist: " + productId);
        }

        if (this.productSubscriptionMap.containsKey(productId) &&
            !this.productSubscriptionMap.get(productId).isEmpty()) {

            throw new IllegalStateException(
                "Product is still referenced by one or more subscriptions: " + productId);
        }

        ProductData pdata = this.productMap.remove(productId);

        // Update our mappings...
        // Impl note: no need to update the subscriptions since we know none are using this product.
        this.productSubscriptionMap.remove(productId);

        // Update content=>product mappings to no longer reference this product
        for (Set<String> pids : this.contentProductMap.values()) {
            pids.remove(productId);
        }

        return pdata;
    }

    public Collection<? extends ProductInfo> listProducts() {
        return this.productMap.values();
    }

    public ProductInfo getProduct(String productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        return this.productMap.get(productId);
    }

    public ContentInfo createContent(ContentInfo cinfo) {
        if (cinfo == null) {
            throw new IllegalArgumentException("cinfo is null");
        }

        if (StringUtils.isBlank(cinfo.getId())) {
            throw new IllegalArgumentException("content is lacking an identifier: " + cinfo);
        }

        if (this.contentMap.containsKey(cinfo.getId())) {
            throw new IllegalStateException("content already exists: " + cinfo.getId());
        }

        // TODO: Add any other content validation we want here

        // Create a copy of the data so we're guaranteed to be detached from any other object
        ContentData cdata = new ContentData();

        cdata.setId(cinfo.getId());
        cdata.setType(cinfo.getType());
        cdata.setLabel(cinfo.getLabel());
        cdata.setName(cinfo.getName());
        cdata.setVendor(cinfo.getVendor());
        cdata.setContentUrl(cinfo.getContentUrl());
        cdata.setRequiredTags(cinfo.getRequiredTags());
        cdata.setReleaseVersion(cinfo.getReleaseVersion());
        cdata.setGpgUrl(cinfo.getGpgUrl());
        cdata.setArches(cinfo.getArches());
        cdata.setMetadataExpiration(cinfo.getMetadataExpiration());
        cdata.setRequiredProductIds(cinfo.getRequiredProductIds());
        cdata.setCreated(new Date());
        cdata.setUpdated(new Date());

        // Create our mappings...
        this.contentMap.put(cdata.getId(), cdata);
        this.contentProductMap.put(cdata.getId(), new HashSet<>());

        return cdata;
    }

    public ContentInfo updateContent(String contentId, ContentInfo cinfo) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        if (cinfo == null) {
            throw new IllegalArgumentException("cinfo is null");
        }

        ContentData cdata = this.contentMap.get(contentId);

        if (cdata == null) {
            throw new IllegalStateException("content does not exist: " + contentId);
        }

        // Don't allow changing the ID

        if (cinfo.getType() != null) {
            cdata.setType(cinfo.getType());
        }

        if (cinfo.getLabel() != null) {
            cdata.setLabel(cinfo.getLabel());
        }

        if (cinfo.getName() != null) {
            cdata.setName(cinfo.getName());
        }

        if (cinfo.getVendor() != null) {
            cdata.setVendor(cinfo.getVendor());
        }

        if (cinfo.getContentUrl() != null) {
            cdata.setContentUrl(cinfo.getContentUrl());
        }

        if (cinfo.getRequiredTags() != null) {
            cdata.setRequiredTags(cinfo.getRequiredTags());
        }

        if (cinfo.getReleaseVersion() != null) {
            cdata.setReleaseVersion(cinfo.getReleaseVersion());
        }

        if (cinfo.getGpgUrl() != null) {
            cdata.setGpgUrl(cinfo.getGpgUrl());
        }

        if (cinfo.getArches() != null) {
            cdata.setArches(cinfo.getArches());
        }

        if (cinfo.getMetadataExpiration() != null) {
            cdata.setMetadataExpiration(cinfo.getMetadataExpiration());
        }

        if (cinfo.getRequiredProductIds() != null) {
            cdata.setRequiredProductIds(cinfo.getRequiredProductIds());
        }

        cdata.setUpdated(new Date());

        return cdata;
    }

    public ContentInfo deleteContent(String contentId) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        ContentData cdata = this.contentMap.remove(contentId);

        if (cdata == null) {
            throw new IllegalStateException("content does not exist: " + contentId);
        }

        // Update every product pointing to this content and update our mappings
        for (String productId : this.contentProductMap.remove(contentId)) {
            ProductData pdata = this.productMap.get(productId);
            pdata.removeContent(contentId);
        }

        return cdata;
    }

    public Collection<? extends ContentInfo> listContent() {
        return this.contentMap.values();
    }

    public ContentInfo getContent(String contentId) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        return this.contentMap.get(contentId);
    }

    public boolean addContentToProduct(String productId, String contentId, boolean enabled) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        ProductData pdata = this.productMap.get(productId);
        if (pdata == null) {
            throw new IllegalArgumentException("No such product: " + productId);
        }

        ContentData cdata = this.contentMap.get(contentId);
        if (cdata == null) {
            throw new IllegalArgumentException("No such content: " + contentId);
        }

        if (pdata.addContent(cdata, enabled)) {
            this.updateProductContentMappings(pdata);
            return true;
        }

        return false;
    }

    public boolean removeContentFromProduct(String productId, String contentId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        ProductData pdata = this.productMap.get(productId);
        if (pdata == null) {
            throw new IllegalArgumentException("No such product: " + productId);
        }

        ContentData cdata = this.contentMap.get(contentId);
        if (cdata == null) {
            throw new IllegalArgumentException("No such content: " + contentId);
        }

        if (pdata.removeContent(contentId)) {
            this.updateProductContentMappings(pdata);
            return true;
        }

        return false;
    }


    protected void updateSubscriptionProductMappings(Subscription sdata) {
        if (sdata == null) {
            throw new IllegalArgumentException("sdata is null");
        }

        if (StringUtils.isBlank(sdata.getId())) {
            throw new IllegalArgumentException("subscription lacks identifying information: " + sdata);
        }

        // Compile list of products this subscription uses...
        Set<String> pids = new HashSet<>();

        if (sdata.getProduct() != null && sdata.getProduct().getId() != null) {
            pids.add(sdata.getProduct().getId());
        }

        if (sdata.getProvidedProducts() != null) {
            for (ProductData pdata : sdata.getProvidedProducts()) {
                if (pdata != null && pdata.getId() != null) {
                    pids.add(pdata.getId());
                }
            }
        }

        if (sdata.getDerivedProduct() != null && sdata.getDerivedProduct().getId() != null) {
            pids.add(sdata.getDerivedProduct().getId());
        }

        if (sdata.getDerivedProvidedProducts() != null) {
            for (ProductData pdata : sdata.getDerivedProvidedProducts()) {
                if (pdata != null && pdata.getId() != null) {
                    pids.add(pdata.getId());
                }
            }
        }

        // Sanity check: Make sure every pid we're refrencing is one we're tracking. This shouldn't
        // ever fail, but if it does, something very bad has happened.
        for (String pid : pids) {
            if (!this.productMap.containsKey(pid)) {
                throw new IllegalStateException("unknown/unexpected product id: " + pid);
            }

            if (!this.productSubscriptionMap.containsKey(pid)) {
                throw new IllegalStateException("product=>subscription map lacks table for product: " + pid);
            }
        }

        // Step through our mapped products and either add the subscription or remove it depending
        // on if the product ID exists in our compiled map
        for (Map.Entry<String, Set<String>> entry : this.productSubscriptionMap.entrySet()) {
            String pid = entry.getKey();
            Set<String> sids = entry.getValue();

            if (pids.contains(pid)) {
                sids.add(sdata.getId());
            }
            else {
                sids.remove(sdata.getId());
            }
        }
    }

    protected void updateProductContentMappings(ProductData pdata) {
        if (pdata == null) {
            throw new IllegalArgumentException("pdata is null");
        }

        if (StringUtils.isBlank(pdata.getId())) {
            throw new IllegalArgumentException("product lacks identifying information: " + pdata);
        }

        // Compile list of content this product uses...
        Set<String> cids = new HashSet<>();

        if (pdata.getProductContent() != null) {
            for (ProductContentData pcdata : pdata.getProductContent()) {
                if (pcdata != null) {
                    ContentData cdata = pcdata.getContent();

                    if (cdata != null && cdata.getId() != null) {
                        cids.add(cdata.getId());
                    }
                }
            }
        }

        // Sanity check: Make sure every cid we're refrencing is one we're tracking. This shouldn't
        // ever fail, but if it does, something very bad has happened.
        for (String cid : cids) {
            if (!this.contentMap.containsKey(cid)) {
                throw new IllegalStateException("unknown/unexpected content id: " + cid);
            }

            if (!this.contentProductMap.containsKey(cid)) {
                throw new IllegalStateException("content=>product map lacks table for content: " + cid);
            }
        }

        // Step through our mapped content and either add the product or remove it depending
        // on if the content ID exists in our compiled set
        for (Map.Entry<String, Set<String>> entry : this.contentProductMap.entrySet()) {
            String cid = entry.getKey();
            Set<String> pids = entry.getValue();

            if (cids.contains(cid)) {
                pids.add(pdata.getId());
            }
            else {
                pids.remove(pdata.getId());
            }
        }
    }


    protected Owner resolveOwner(OwnerInfo oinfo) {
        if (oinfo == null || StringUtils.isBlank(oinfo.getKey())) {
            throw new IllegalArgumentException("owner is null or lacks an owner key");
        }

        Owner owner = this.ownerMap.get(oinfo.getKey());

        if (owner == null) {
            owner = new Owner(oinfo.getKey());
            this.ownerMap.put(owner.getKey(), owner);
        }

        return owner;
    }

    protected ProductData resolveProduct(ProductInfo pinfo) {
        if (pinfo != null) {
            if (StringUtils.isBlank(pinfo.getId())) {
                throw new IllegalArgumentException("product lacks an identifier: " + pinfo);
            }

            ProductData pdata = this.productMap.get(pinfo.getId());
            if (pdata == null) {
                throw new IllegalStateException("product does not exist: " + pinfo.getId());
            }

            return pdata;
        }

        return null;
    }

    protected Collection<ProductData> resolveProducts(Collection<? extends ProductInfo> products) {
        if (products != null) {
            Map<String, ProductData> output = new HashMap<>();

            for (ProductInfo pinfo : products) {
                ProductData pdata = this.resolveProduct(pinfo);

                if (pdata != null) {
                    output.put(pdata.getId(), pdata);
                }
            }

            return output.values();
        }

        return null;
    }

    protected Collection<ProductContentData> resolveProductContent(
        Collection<? extends ProductContentInfo> productContent) {

        if (productContent != null) {
            Map<String, ProductContentData> output = new HashMap<>();

            for (ProductContentInfo pcinfo : productContent) {
                if (pcinfo != null) {
                    ContentInfo cinfo = pcinfo.getContent();

                    if (cinfo == null || StringUtils.isBlank(cinfo.getId())) {
                        throw new IllegalArgumentException(
                            "content is null or lacks an identifier: " + cinfo);
                    }

                    ContentData cdata = this.contentMap.get(cinfo.getId());

                    if (cdata == null) {
                        throw new IllegalStateException("content does not exist: " + cinfo.getId());
                    }

                    ProductContentData pcdata = new ProductContentData();
                    pcdata.setContent(cdata);
                    pcdata.setEnabled(pcinfo.isEnabled() != null ?
                        pcinfo.isEnabled() :
                        ProductContent.DEFAULT_ENABLED_STATE);

                    output.put(cinfo.getId(), pcdata);
                }
            }

            return output.values();
        }

        return null;
    }

    protected Set<Branding> resolveBranding(Collection<? extends BrandingInfo> branding) {
        if (branding != null) {
            Map<String, Branding> brandMap = new HashMap<>();

            // We don't bother keeping cross-subscription references here, so the only thing
            // we are really concerned with is making sure we don't end up with two brands
            // for the same product.

            for (BrandingInfo binfo : branding) {
                // Silently ignore null refs
                if (binfo != null) {
                    if (binfo.getProductId() == null || binfo.getProductId().isEmpty()) {
                        throw new IllegalArgumentException("Branding lacks a product ID: " + binfo);
                    }

                    Branding bdata = new Branding();

                    bdata.setProductId(binfo.getProductId());
                    bdata.setType(binfo.getType());
                    bdata.setName(binfo.getName());

                    brandMap.put(bdata.getProductId(), bdata);
                }
            }

            return new HashSet<>(brandMap.values());
        }

        return null;
    }

    protected Cdn convertCdn(CdnInfo cinfo) {
        if (cinfo != null) {
            if (cinfo.getName() == null || cinfo.getName().isEmpty()) {
                throw new IllegalArgumentException("Cdn lacks a name: " + cinfo);
            }

            Cdn cdata = new Cdn();

            cdata.setName(cinfo.getName());
            cdata.setLabel(cinfo.getLabel());
            cdata.setUrl(cinfo.getUrl());
            cdata.setCertificate(this.convertCdnCertificate(cinfo.getCertificate()));

            return cdata;
        }

        return null;
    }

    protected CdnCertificate convertCdnCertificate(CertificateInfo cinfo) {
        if (cinfo != null) {
            CdnCertificate cert = new CdnCertificate();

            cert.setKey(cinfo.getKey());
            cert.setCert(cinfo.getCertificate());

            if (cinfo.getSerial() != null) {
                CertificateSerialInfo serialInfo = cinfo.getSerial();
                CertificateSerial serial = new CertificateSerial();

                serial.setSerial(serialInfo.getSerial());
                serial.setRevoked(serialInfo.isRevoked());
                serial.setCollected(serialInfo.isCollected());
                serial.setExpiration(serialInfo.getExpiration());

                cert.setSerial(serial);
            }

            return cert;
        }

        return null;
    }

    protected SubscriptionsCertificate convertSubscriptionCertificate(CertificateInfo cinfo) {
        if (cinfo != null) {
            SubscriptionsCertificate cert = new SubscriptionsCertificate();

            cert.setKey(cinfo.getKey());
            cert.setCert(cinfo.getCertificate());

            if (cinfo.getSerial() != null) {
                CertificateSerialInfo serialInfo = cinfo.getSerial();
                CertificateSerial serial = new CertificateSerial();

                serial.setSerial(serialInfo.getSerial());
                serial.setRevoked(serialInfo.isRevoked());
                serial.setCollected(serialInfo.isCollected());
                serial.setExpiration(serialInfo.getExpiration());

                cert.setSerial(serial);
            }

            return cert;
        }

        return null;
    }



    /**
     * Clears all data for this service adapter
     */
    public void clearData() {
        this.ownerMap.clear();
        this.subscriptionMap.clear();
        this.productMap.clear();
        this.contentMap.clear();
        this.contentProductMap.clear();
        this.productSubscriptionMap.clear();
    }



    // Service adapter interface methods

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<? extends SubscriptionInfo> getSubscriptions() {
        return this.subscriptionMap.values();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionInfo getSubscription(String subscriptionId) {
        return this.subscriptionMap.get(subscriptionId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<? extends SubscriptionInfo> getSubscriptions(String ownerKey) {
        if (ownerKey != null) {
            return this.subscriptionMap.values()
                .stream()
                .filter(s -> s.getOwner() != null && ownerKey.equals(s.getOwner().getKey()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getSubscriptionIds(String ownerKey) {
        if (ownerKey != null) {
            return this.subscriptionMap.values()
                .stream()
                .filter(s -> s.getOwner() != null && ownerKey.equals(s.getOwner().getKey()))
                .map(s -> s.getId())
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<? extends SubscriptionInfo> getSubscriptionsByProductId(String productId) {
        if (productId != null) {
            Predicate<Subscription> predicate = sub -> {
                if (sub.getProduct() != null && productId.equals(sub.getProduct().getId())) {
                    return true;
                }

                if (sub.getProvidedProducts() != null) {
                    for (ProductData product : sub.getProvidedProducts()) {
                        if (product != null && productId.equals(product.getId())) {
                            return true;
                        }
                    }
                }

                return false;
            };

            return this.subscriptionMap.values()
                .stream()
                .filter(predicate)
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasUnacceptedSubscriptionTerms(String ownerKey) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendActivationEmail(String subscriptionId) {
        // method intentionally left blank
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canActivateSubscription(ConsumerInfo consumer) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void activateSubscription(ConsumerInfo consumer, String email, String emailLocale) {
        // method intentionally left blank
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReadOnly() {
        return false;
    }

}
