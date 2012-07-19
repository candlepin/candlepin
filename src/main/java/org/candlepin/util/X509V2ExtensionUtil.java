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
package org.candlepin.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.DeflaterOutputStream;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.candlepin.config.Config;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.Subscription;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509ExtensionWrapper;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

/**
 * X509ExtensionUtil
 */
public class X509V2ExtensionUtil {

    private static Logger log = Logger.getLogger(X509V2ExtensionUtil.class);
    private Config config;
    private EntitlementCurator entCurator;
    private String thisVersion = "2.0";


    @Inject
    public X509V2ExtensionUtil(Config config, EntitlementCurator entCurator) {
        // Output everything in UTC
        this.config = config;
        this.entCurator = entCurator;
    }

    public Set<X509ExtensionWrapper> getExtensions(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent, Subscription sub) {
        Set<X509ExtensionWrapper> toReturn = new LinkedHashSet<X509ExtensionWrapper>();

        X509ExtensionWrapper versionExtension =
            new X509ExtensionWrapper(OIDUtil.REDHAT_OID + "." +
                OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ENTITLEMENT_VERSION_KEY),
                false, thisVersion);

        toReturn.add(versionExtension);

        return toReturn;
    }

    public Set<X509ByteExtensionWrapper> getByteExtensions(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent, Subscription sub) {
        Set<X509ByteExtensionWrapper> toReturn =
            new LinkedHashSet<X509ByteExtensionWrapper>();

        Map<String, Object> map = createEntitlementBodyMap(products, ent,
            contentPrefix, promotedContent, sub);

        String payload = Util.toJson(map);
        log.debug(payload);
        byte[] value = null;
        try {
            value = processPayload(payload);
        }
        catch (Exception e) {
            //no-op
        }

        X509ByteExtensionWrapper bodyExtension =
            new X509ByteExtensionWrapper(OIDUtil.REDHAT_OID + "." +
                OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ENTITLEMENT_DATA_KEY),
                false, value);
        toReturn.add(bodyExtension);

        return toReturn;
    }

    private byte[] processPayload(String payload)
        throws IOException, UnsupportedEncodingException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeflaterOutputStream dos = new DeflaterOutputStream(baos);
        dos.write(payload.getBytes("UTF-8"));
        dos.finish();
        dos.close();
        return baos.toByteArray();
    }

    public Map<String, Object> createEntitlementBodyMap(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent, Subscription sub) {
        Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put("consumer", ent.getConsumer().getUuid());
        toReturn.put("quantity", ent.getQuantity());
        toReturn.put("subscription", mapSubscription(sub, ent));
        toReturn.put("order", mapOrder(sub));
        toReturn.put("products", mapProducts(products, contentPrefix, promotedContent,
            ent.getConsumer(), ent));

        return toReturn;
    }

    public Map<String, Object> mapSubscription(Subscription sub, Entitlement ent) {
        Map<String, Object> toReturn = new HashMap<String, Object>();

        toReturn.put("sku", sub.getProduct().getId().toString());
        toReturn.put("name", sub.getProduct().getName());

        String warningPeriod = sub.getProduct().getAttributeValue(
            "warning_period");
        if (warningPeriod != null && !warningPeriod.trim().equals("")) {
            // only included if not the default value of 0
            if (!warningPeriod.equals("0")) {
                toReturn.put("warning", new Integer(warningPeriod));
            }
        }

        String socketLimit = sub.getProduct().getAttributeValue("sockets");
        if (socketLimit != null && !socketLimit.trim().equals("")) {
            toReturn.put("sockets", new Integer(socketLimit));
        }

        String management = sub.getProduct().getAttributeValue("management_enabled");
        if (management != null && !management.trim().equals("")) {
            // only included if not the default value of false
            Boolean m = new Boolean(management);
            if (m) {
                toReturn.put("management", m);
            }
        }

        String stackingId = sub.getProduct().getAttributeValue("stacking_id");
        if (stackingId != null && !stackingId.trim().equals("")) {
            toReturn.put("stacking_id", stackingId);
        }

        String virtOnly = ent.getPool().getAttributeValue("virt_only");
        if (virtOnly != null && !virtOnly.trim().equals("")) {
            // only included if not the default value of false
            Boolean vo = new Boolean(virtOnly);
            if (vo) {
                toReturn.put("virt_only", vo);
            }
        }

        Map<String, Object> service = mapService(sub);
        if (service.size() > 0) {
            toReturn.put("service", mapService(sub));
        }

        return toReturn;
    }

    private Map<String, Object> mapService(Subscription sub) {
        Map<String, Object> toReturn = new HashMap<String, Object>();

        String supportLevel = sub.getProduct().getAttributeValue("support_level");
        if (supportLevel != null) {
            toReturn.put("level", supportLevel);
        }

        String supportType = sub.getProduct().getAttributeValue("support_type");
        if (supportType != null) {
            toReturn.put("type", supportType);
        }

        return toReturn;
    }

    public Map<String, Object> mapOrder(Subscription sub) {
        SimpleDateFormat iso8601DateFormat = Util.getUTCDateFormat();
        Map<String, Object> toReturn = new HashMap<String, Object>();

        toReturn.put("number", sub.getId().toString());
        toReturn.put("quantity", sub.getQuantity());
        toReturn.put("start", iso8601DateFormat.format(sub.getStartDate()));
        toReturn.put("end", iso8601DateFormat.format(sub.getEndDate()));

        if (sub.getContractNumber() != null &&
            !sub.getContractNumber().trim().equals("")) {
            toReturn.put("contract", sub.getContractNumber());
        }

        if (sub.getAccountNumber() != null &&
            !sub.getAccountNumber().trim().equals("")) {
            toReturn.put("account", sub.getAccountNumber());
        }

        return toReturn;
    }

    public List<Map<String, Object>> mapProducts(Set<Product> products,
        String contentPrefix, Map<String, EnvironmentContent> promotedContent,
        Consumer consumer, Entitlement ent) {
        List<Map<String, Object>> toReturn = new ArrayList<Map<String, Object>>();

        for (Product p : Collections2
            .filter(products, PROD_FILTER_PREDICATE)) {
            toReturn.add(mapProduct(p, contentPrefix, promotedContent, consumer, ent));
        }
        return toReturn;
    }

    private Map<String, Object> mapProduct(Product product, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent, Consumer consumer,
        Entitlement ent) {
        Map<String, Object> toReturn = new HashMap<String, Object>();

        toReturn.put("id", product.getId());
        toReturn.put("name", product.getName());

        String version = product.hasAttribute("version") ?
            product.getAttributeValue("version") : "";
        toReturn.put("version", version);

        String arch = product.hasAttribute("arch") ?
            product.getAttributeValue("arch") : "";
        StringTokenizer st = new StringTokenizer(arch, ",");
        List<String> archList = new ArrayList<String>();
        while (st.hasMoreElements()) {
            archList.add((String) st.nextElement());
        }
        toReturn.put("architectures", archList);

        toReturn.put("content", mapContent(filterProductContent(product, ent),
            contentPrefix, promotedContent, consumer));

        return toReturn;
    }

    public List<Map<String, Object>> mapContent(
        Set<ProductContent> productContent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent, Consumer consumer) {

        List<Map<String, Object>> toReturn = new ArrayList<Map<String, Object>>();

        boolean enableEnvironmentFiltering = config.environmentFileringEnabled();

        for (ProductContent pc : productContent) {
            Map<String, Object> data = new HashMap<String, Object>();
            if (enableEnvironmentFiltering) {
                if (consumer.getEnvironment() != null && !promotedContent.containsKey(
                    pc.getContent().getId())) {
                    log.debug("Skipping content not promoted to environment: " +
                        pc.getContent().getId());
                    continue;
                }
            }

            // augment the content path with the prefix if it is passed in
            String contentPath = pc.getContent().getContentUrl();
            if (contentPrefix != null) {
                contentPath = contentPrefix + contentPath;
            }

            data.put("id", pc.getContent().getId());
            data.put("type", pc.getContent().getType());
            data.put("name", pc.getContent().getName());
            data.put("label", pc.getContent().getLabel());
            data.put("vendor", pc.getContent().getVendor());
            data.put("path", contentPath);
            data.put("gpg_url", pc.getContent().getGpgUrl());

            // Check if we should override the enabled flag due to setting on promoted
            // content:
            Boolean enabled = pc.getEnabled();
            log.debug("default enabled flag = " + enabled);
            if ((consumer.getEnvironment() != null) && enableEnvironmentFiltering) {
                // we know content has been promoted at this point:
                Boolean enabledOverride = promotedContent.get(
                    pc.getContent().getId()).getEnabled();
                if (enabledOverride != null) {
                    log.debug("overriding enabled flag: " + enabledOverride);
                    enabled = enabledOverride;
                }
            }
            // only included if not the default value of true
            if (!enabled) {
                data.put("enabled", enabled);
            }

            // Include metadata expiry if specified on the content:
            if (pc.getContent().getMetadataExpire() != null) {
                data.put("metadata_expire", pc.getContent().getMetadataExpire());
            }

            // Include required tags if specified on the content set:
            String requiredTags = pc.getContent().getRequiredTags();
            if ((requiredTags != null) && !requiredTags.equals("")) {
                StringTokenizer st = new StringTokenizer(requiredTags, ",");
                List<String> tagList = new ArrayList<String>();
                while (st.hasMoreElements()) {
                    tagList.add((String) st.nextElement());
                }
                data.put("required_tags", tagList);
            }
            toReturn.add(data);
        }
        return toReturn;
    }

    /**
     * Scan the product content looking for any which modify some other product. If found
     * we must check that this consumer has another entitlement granting them access
     * to that modified product. If they do not, we should filter out this content.
     *
     * @param prod
     * @param ent
     * @return ProductContent to include in the certificate.
     */
    public Set<ProductContent> filterProductContent(Product prod, Entitlement ent) {
        Set<ProductContent> filtered = new HashSet<ProductContent>();

        for (ProductContent pc : prod.getProductContent()) {
            boolean include = true;
            if (pc.getContent().getModifiedProductIds().size() > 0) {
                include = false;
                Set<String> prodIds = pc.getContent().getModifiedProductIds();
                // If consumer has an entitlement to just one of the modified products,
                // we will include this content set:
                for (String prodId : prodIds) {
                    Set<Entitlement> entsProviding = entCurator.listProviding(
                        ent.getConsumer(), prodId, ent.getStartDate(), ent.getEndDate());
                    if (entsProviding.size() > 0) {
                        include = true;
                        break;
                    }
                }
            }

            if (include) {
                filtered.add(pc);
            }
            else {
                log.debug("No entitlements found for modified products.");
                log.debug("Skipping content set: " + pc.getContent());
            }
        }
        return filtered;
    }

    private static final Predicate<Product>
    PROD_FILTER_PREDICATE = new Predicate<Product>() {
        @Override
        public boolean apply(Product product) {
            return product != null && StringUtils.isNumeric(product.getId());
        }
    };
}
