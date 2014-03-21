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

import org.candlepin.config.Config;
import org.candlepin.json.model.Content;
import org.candlepin.json.model.EntitlementBody;
import org.candlepin.json.model.Order;
import org.candlepin.json.model.Service;
import org.candlepin.json.model.Subscription;
import org.candlepin.model.Branding;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509ExtensionWrapper;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

/**
 * X509ExtensionUtil
 */
public class X509V3ExtensionUtil extends X509Util {

    private static Logger log = LoggerFactory.getLogger(X509V3ExtensionUtil.class);
    private Config config;
    private EntitlementCurator entCurator;
    private String thisVersion = "3.2";

    private long pathNodeId = 0;
    private long huffNodeId = 0;
    private static final Object END_NODE = new Object();
    private static boolean treeDebug = false;
    @Inject
    public X509V3ExtensionUtil(Config config, EntitlementCurator entCurator) {
        // Output everything in UTC
        this.config = config;
        this.entCurator = entCurator;
    }

    public Set<X509ExtensionWrapper> getExtensions(Entitlement ent,
        String contentPrefix, Map<String, EnvironmentContent> promotedContent) {
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
        Map<String, EnvironmentContent> promotedContent) throws IOException {
        Set<X509ByteExtensionWrapper> toReturn =
            new LinkedHashSet<X509ByteExtensionWrapper>();

        EntitlementBody eb = createEntitlementBodyContent(products, ent,
            contentPrefix, promotedContent);

        X509ByteExtensionWrapper bodyExtension =
            new X509ByteExtensionWrapper(OIDUtil.REDHAT_OID + "." +
                OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ENTITLEMENT_DATA_KEY),
                false, retreiveContentValue(eb));
        toReturn.add(bodyExtension);

        return toReturn;
    }

    public byte[] createEntitlementDataPayload(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent)
        throws UnsupportedEncodingException, IOException {

        EntitlementBody map = createEntitlementBody(products, ent,
            contentPrefix, promotedContent);

        String json = toJson(map);
        return processPayload(json);
    }


    private byte[] retreiveContentValue(EntitlementBody eb) throws IOException {
        List<Content> contentList = getContentList(eb);
        PathNode treeRoot = makePathTree(contentList, new PathNode());
        List<String> nodeStrings = orderStrings(treeRoot);
        if (nodeStrings.size() == 0) {
            return new byte[0];
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        List<HuffNode> stringHuffNodes = getStringNodeList(nodeStrings);
        HuffNode stringTrieParent = makeTrie(stringHuffNodes);
        data.write(byteProcess(nodeStrings));

        List<PathNode> orderedNodes = orderNodes(treeRoot);
        List<HuffNode> pathNodeHuffNodes = getPathNodeNodeList(orderedNodes);
        HuffNode pathNodeTrieParent = makeTrie(pathNodeHuffNodes);
        data.write(makeNodeDictionary(stringTrieParent,
            pathNodeTrieParent, orderedNodes));

        return data.toByteArray();
    }

    public EntitlementBody createEntitlementBody(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent) {

        EntitlementBody toReturn = new EntitlementBody();
        toReturn.setConsumer(ent.getConsumer().getUuid());
        toReturn.setQuantity(ent.getQuantity());
        toReturn.setSubscription(createSubscription(ent));
        toReturn.setOrder(createOrder(ent.getPool()));
        toReturn.setProducts(createProducts(products, contentPrefix, promotedContent,
            ent.getConsumer(), ent));
        toReturn.setPool(createPool(ent));

        return toReturn;
    }

    public EntitlementBody createEntitlementBodyContent(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent) {

        EntitlementBody toReturn = new EntitlementBody();
        toReturn.setProducts(createProducts(products, contentPrefix, promotedContent,
            ent.getConsumer(), ent));

        return toReturn;
    }

    public Subscription createSubscription(
        Entitlement ent) {
        Subscription toReturn = new Subscription();
        Pool pool = ent.getPool();

        toReturn.setSku(pool.getProductId());
        toReturn.setName(pool.getProductName());

        String warningPeriod = pool.getProductAttributeValue(
            "warning_period");
        if (warningPeriod != null && !warningPeriod.trim().equals("")) {
            // only included if not the default value of 0
            if (!warningPeriod.equals("0")) {
                toReturn.setWarning(new Integer(warningPeriod));
            }
        }

        String socketLimit = pool.getProductAttributeValue("sockets");
        if (socketLimit != null && !socketLimit.trim().equals("")) {
            toReturn.setSockets(new Integer(socketLimit));
        }

        String ramLimit = pool.getProductAttributeValue("ram");
        if (ramLimit != null && !ramLimit.trim().equals("")) {
            toReturn.setRam(new Integer(ramLimit));
        }

        String coreLimit = pool.getProductAttributeValue("cores");
        if (coreLimit != null && !coreLimit.trim().equals("")) {
            toReturn.setCores(new Integer(coreLimit));
        }

        String management = pool.getProductAttributeValue("management_enabled");
        if (management != null && !management.trim().equals("")) {
            // only included if not the default value of false
            if (management.equalsIgnoreCase("true") ||
                    management.equalsIgnoreCase("1")) {
                toReturn.setManagement(Boolean.TRUE);
            }
        }

        String stackingId = pool.getProductAttributeValue("stacking_id");
        if (stackingId != null && !stackingId.trim().equals("")) {
            toReturn.setStackingId(stackingId);
        }

        String virtOnly = ent.getPool().getAttributeValue("virt_only");
        if (virtOnly != null && !virtOnly.trim().equals("")) {
            // only included if not the default value of false
            Boolean vo = Boolean.valueOf(virtOnly.equalsIgnoreCase("true") ||
                virtOnly.equalsIgnoreCase("1"));
            if (vo) {
                toReturn.setVirtOnly(vo);
            }
        }

        toReturn.setService(createService(pool));
        return toReturn;
    }

    private Service createService(Pool pool) {
        if (pool.getProductAttributeValue("support_level") == null &&
            pool.getProductAttributeValue("support_type") == null) {
            return null;
        }
        Service toReturn = new Service();
        toReturn.setLevel(pool.getProductAttributeValue("support_level"));
        toReturn.setType(pool.getProductAttributeValue("support_type"));

        return toReturn;
    }

    public Order createOrder(Pool pool) {
        SimpleDateFormat iso8601DateFormat = Util.getUTCDateFormat();
        Order toReturn = new Order();

        toReturn.setNumber(pool.getOrderNumber());
        toReturn.setQuantity(pool.getQuantity());
        toReturn.setStart(iso8601DateFormat.format(pool.getStartDate()));
        toReturn.setEnd(iso8601DateFormat.format(pool.getEndDate()));

        if (pool.getContractNumber() != null &&
            !pool.getContractNumber().trim().equals("")) {
            toReturn.setContract(pool.getContractNumber());
        }

        if (pool.getAccountNumber() != null &&
            !pool.getAccountNumber().trim().equals("")) {
            toReturn.setAccount(pool.getAccountNumber());
        }

        return toReturn;
    }

    public List<org.candlepin.json.model.Product> createProducts(Set<Product> products,
        String contentPrefix, Map<String, EnvironmentContent> promotedContent,
        Consumer consumer, Entitlement ent) {
        List<org.candlepin.json.model.Product> toReturn =
            new ArrayList<org.candlepin.json.model.Product>();

        for (Product p : Collections2
            .filter(products, PROD_FILTER_PREDICATE)) {
            toReturn.add(mapProduct(p, contentPrefix, promotedContent, consumer, ent));
        }
        return toReturn;
    }

    public org.candlepin.json.model.Pool createPool(Entitlement ent) {
        org.candlepin.json.model.Pool toReturn = new org.candlepin.json.model.Pool();
        toReturn.setId(ent.getPool().getId());
        return toReturn;
    }

    private org.candlepin.json.model.Product mapProduct(Product product,
        String contentPrefix, Map<String, EnvironmentContent> promotedContent,
        Consumer consumer, Entitlement ent) {

        org.candlepin.json.model.Product toReturn = new org.candlepin.json.model.Product();

        toReturn.setId(product.getId());
        toReturn.setName(product.getName());

        String version = product.hasAttribute("version") ?
            product.getAttributeValue("version") : "";
        toReturn.setVersion(version);

        Branding brand = getBranding(ent.getPool(), product.getId());
        toReturn.setBrandType(brand.getType());
        toReturn.setBrandName(brand.getName());

        String productArches = product.getAttributeValue("arch");
        Set<String> productArchSet = Arch.parseArches(productArches);

        // FIXME: getParsedArches might make more sense to just return a list
        List<String> archList = new ArrayList<String>();
        for (String arch : productArchSet) {
            archList.add(arch);
        }
        toReturn.setArchitectures(archList);
        toReturn.setContent(createContent(filterProductContent(product, ent),
            contentPrefix, promotedContent, consumer, product));

        return toReturn;
    }

    /*
     * Return a branding object for the given engineering product ID if one exists for
     * the pool in question.
     */
    private Branding getBranding(Pool pool, String productId) {
        Branding resultBranding = null;
        for (Branding b : pool.getBranding()) {
            if (b.getProductId().equals(productId)) {
                if (resultBranding == null) {
                    resultBranding = b;
                }
                else {
                    // Warn, but use the first brand name we encountered:
                    log.warn("Found multiple brand names: product={}, contract={}, " +
                        "owner={}", productId, pool.getContractNumber(),
                        pool.getOwner().getKey());
                }
            }
        }
        // If none exist, use null strings
        return resultBranding != null ? resultBranding :
            new Branding(productId, null, null);
    }

    /*
     * createContent
     *
     * productArchList is a list of arch strings parse from
     *   product attributes.
     */
    public List<Content> createContent(
        Set<ProductContent> productContent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent,
        Consumer consumer, Product product) {

        List<Content> toReturn = new ArrayList<Content>();

        boolean enableEnvironmentFiltering = config.environmentFilteringEnabled();

        // Return only the contents that are arch appropriate
        Set<ProductContent> archApproriateProductContent = filterContentByContentArch(
            productContent, consumer, product);

        for (ProductContent pc : archApproriateProductContent) {
            Content content = new Content();
            if (enableEnvironmentFiltering) {
                if (consumer.getEnvironment() != null && !promotedContent.containsKey(
                    pc.getContent().getId())) {
                    log.debug("Skipping content not promoted to environment: " +
                        pc.getContent().getId());
                    continue;
                }
            }

            // Augment the content path with the prefix if it is passed in
            String contentPath = this.createFullContentPath(contentPrefix, pc);

            content.setId(pc.getContent().getId());
            content.setType(pc.getContent().getType());
            content.setName(pc.getContent().getName());
            content.setLabel(pc.getContent().getLabel());
            content.setVendor(pc.getContent().getVendor());
            content.setPath(contentPath);
            content.setGpgUrl(pc.getContent().getGpgUrl());


            // Set content model's arches here, inheriting from the product if
            // they are not set on the content.
            List<String> archesList = new ArrayList<String>();

            Set<String> contentArches = Arch.parseArches(pc.getContent()
                .getArches());
            if (contentArches.isEmpty()) {
                archesList.addAll(Arch.parseArches(product
                    .getAttributeValue(PRODUCT_ARCH_ATTR)));
            }
            else {
                archesList
                    .addAll(Arch.parseArches(pc.getContent().getArches()));
            }
            content.setArches(archesList);

            // Check if we should override the enabled flag due to setting on promoted
            // content
            Boolean enabled = pc.getEnabled();
            if ((consumer.getEnvironment() != null) && enableEnvironmentFiltering) {
                // we know content has been promoted at this point
                Boolean enabledOverride = promotedContent.get(
                    pc.getContent().getId()).getEnabled();
                if (enabledOverride != null) {
                    log.debug("overriding enabled flag: " + enabledOverride);
                    enabled = enabledOverride;
                }
            }
            // only included if not the default value of true
            if (!enabled) {
                content.setEnabled(enabled);
            }

            // Include metadata expiry if specified on the content
            if (pc.getContent().getMetadataExpire() != null) {
                content.setMetadataExpire(pc.getContent().getMetadataExpire());
            }

            // Include required tags if specified on the content set
            String requiredTags = pc.getContent().getRequiredTags();
            if ((requiredTags != null) && !requiredTags.equals("")) {
                StringTokenizer st = new StringTokenizer(requiredTags, ",");
                List<String> tagList = new ArrayList<String>();
                while (st.hasMoreElements()) {
                    tagList.add((String) st.nextElement());
                }
                content.setRequiredTags(tagList);
            }
            toReturn.add(content);
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
                // we will include this content set
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

    protected List<Content> getContentList(EntitlementBody eb) {
        // collect content URL's
        List<Content> contentList = new ArrayList<Content>();
        for (org.candlepin.json.model.Product p : eb.getProducts()) {
            for (org.candlepin.json.model.Content c : p.getContent()) {
                contentList.add(c);
            }
        }
        return contentList;
    }

    public PathNode makePathTree(List<Content> contents, PathNode parent) {
        PathNode endMarker = new PathNode();
        for (Content c : contents) {
            String path = c.getPath();

            if (treeDebug) {
                log.debug(path);
            }
            StringTokenizer st = new StringTokenizer(path, "/");
            makePathForURL(st, parent, endMarker);
        }
        if (treeDebug) { printTree(parent, 0); }
        condenseSubTreeNodes(endMarker);
        if (treeDebug) { printTree(parent, 0); }
        return parent;
    }

    public void printTree(PathNode pn, int tab) {
        StringBuffer nodeRep = new StringBuffer();
        for (int i = 0; i <= tab; i++) {
            nodeRep.append("  ");
        }
        nodeRep.append("Node [");
        nodeRep.append(pn.getId());
        nodeRep.append("]");

        for (PathNode parent : pn.getParents()) {
            nodeRep.append(" ^ [");
            nodeRep.append(parent.getId());
            nodeRep.append("]");
        }
        for (NodePair cp : pn.getChildren()) {
            nodeRep.append(" v [");
            nodeRep.append(cp.getName());
            nodeRep.append(" {");
            nodeRep.append(cp.getConnection().getId());
            nodeRep.append("} ]");
        }
        log.debug("{}", nodeRep);
        for (NodePair cp : pn.getChildren()) {
            printTree(cp.getConnection(), tab + 1);
        }
    }

    public void printTrie(HuffNode hn, int tab) {
        StringBuffer nodeRep = new StringBuffer();
        for (int i = 0; i <= tab; i++) {
            nodeRep.append("  ");
        }
        nodeRep.append("Node [");
        nodeRep.append(hn.getId());
        nodeRep.append("]");

        nodeRep.append(", Weight [");
        nodeRep.append(hn.getWeight());
        nodeRep.append("]");

        nodeRep.append(", Value = [");
        nodeRep.append(hn.getValue());
        nodeRep.append("]");

        log.debug("{}", nodeRep);
        if (hn.getLeft() != null) {
            printTrie(hn.getLeft(), tab + 1);
        }
        if (hn.getRight() != null) {
            printTrie(hn.getRight(), tab + 1);
        }
    }

    private void makePathForURL(StringTokenizer st, PathNode parent, PathNode endMarker) {
        if (st.hasMoreTokens()) {
            String childVal = st.nextToken();
            if (childVal.equals("")) { return; }
            boolean isNew = true;
            for (NodePair child : parent.getChildren()) {
                if (child.getName().equals(childVal) &&
                    !child.getConnection().equals(endMarker)) {
                    makePathForURL(st, child.getConnection(), endMarker);
                    isNew = false;
                }
            }
            if (isNew) {
                PathNode next = null;
                if (st.hasMoreTokens()) {
                    next = new PathNode();
                    parent.addChild(new NodePair(childVal, next));
                    next.addParent(parent);
                    makePathForURL(st, next, endMarker);
                }
                else {
                    parent.addChild(new NodePair(childVal, endMarker));
                    if (!endMarker.getParents().contains(parent)) {
                        endMarker.addParent(parent);
                    }
                }
            }
        }
    }

    private void condenseSubTreeNodes(PathNode location) {
        // "equivalent" parents are merged
        List<PathNode> parentResult = new ArrayList<PathNode>();
        parentResult.addAll(location.getParents());
        for (PathNode parent1 : location.getParents()) {
            if (!parentResult.contains(parent1)) {
                continue;
            }
            for (PathNode parent2 : location.getParents()) {
                if (!parentResult.contains(parent2) ||
                    parent2.getId() == parent1.getId()) {
                    continue;
                }
                if (parent1.isEquivalentTo(parent2)) {
                    // we merge them into smaller Id
                    PathNode merged = parent1.getId() < parent2.getId() ?
                        parent1 : parent2;
                    PathNode toRemove = parent1.getId() < parent2.getId() ?
                        parent2 : parent1;

                    // track down the name of the string in the grandparent
                    //  that points to parent
                    String name = "";
                    PathNode oneParent = toRemove.getParents().get(0);
                    for (NodePair child : oneParent.getChildren()) {
                        if (child.getConnection().getId() == toRemove.getId()) {
                            name = child.getName();
                            break;
                        }
                    }

                    // copy grandparents to merged parent node.
                    List<PathNode> movingParents = toRemove.getParents();
                    merged.addParents(movingParents);

                    // all grandparents with name now point to merged node
                    for (PathNode pn : toRemove.getParents()) {
                        for (NodePair child : pn.getChildren()) {
                            if (child.getName().equals(name)) {
                                child.setConnection(merged);
                            }
                        }
                    }
                    parentResult.remove(toRemove);
                }
            }
        }
        location.setParents(parentResult);
        for (PathNode pn : location.getParents()) {
            condenseSubTreeNodes(pn);
        }
    }

    public List<String> orderStrings(PathNode parent) throws IOException {
        List<String> parts = new ArrayList<String>();
        // walk tree to make string map
        Map<String, Integer> segments =  new HashMap<String, Integer>();
        Set<PathNode> nodes =  new HashSet<PathNode>();
        buildSegments(segments, nodes, parent);
        for (Entry<String, Integer> entry : segments.entrySet()) {
            String part = entry.getKey();
            if (!part.equals("")) {
                int count = entry.getValue();
                if (parts.size() == 0) {
                    parts.add(part);
                }
                else {
                    int pos = parts.size();
                    for (int i = 0; i < parts.size(); i++) {
                        if (count < segments.get(parts.get(i))) {
                            pos = i;
                            break;
                        }
                    }
                    parts.add(pos, part);
                }
            }
        }
        if (treeDebug) {
            log.debug("Parts List: " + parts);
        }
        return parts;
    }

    private void buildSegments(Map<String, Integer> segments,
        Set<PathNode> nodes, PathNode parent) {
        if (!nodes.contains(parent)) {
            nodes.add(parent);
            for (NodePair np : parent.getChildren()) {
                Integer count = segments.get(np.getName());
                if (count == null) {
                    count = 0;
                }
                segments.put(np.getName(), ++count);
                buildSegments(segments, nodes, np.getConnection());
            }
        }
    }

    private List<PathNode> orderNodes(PathNode treeRoot) {
        List<PathNode> result = new ArrayList<PathNode>();

        // walk tree to make string map
        Set<PathNode> nodes =  getPathNodes(treeRoot);
        for (PathNode pn : nodes) {
            int count = pn.getParents().size();
            if (nodes.size() == 0) {
                nodes.add(pn);
            }
            else {
                int pos = result.size();
                for (int i = 0; i < result.size(); i++) {
                    if (count <= result.get(i).getParents().size()) {
                        pos = i;
                        break;
                    }
                }
                result.add(pos, pn);
            }
        }
        if (treeDebug) {
            log.debug("{}", result);
        }
        return result;
    }

    private Set<PathNode> getPathNodes(PathNode treeRoot) {
        Set<PathNode> nodes = new HashSet<PathNode>();
        nodes.add(treeRoot);
        for (NodePair np : treeRoot.getChildren()) {
            nodes.addAll(getPathNodes(np.getConnection()));
        }
        return nodes;
    }

    private byte[] makeNodeDictionary(HuffNode stringParent,
        HuffNode pathNodeParent, List<PathNode> pathNodes)
        throws UnsupportedEncodingException, IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int nodeSize = pathNodes.size();
        if (nodeSize > 127) {
            ByteArrayOutputStream countBaos = new ByteArrayOutputStream();
            boolean start = false;
            for (byte b : toByteArray(nodeSize)) {
                if (b == 0 && !start) {
                    continue;
                }
                else {
                    countBaos.write(b);
                    start = true;
                }
            }
            baos.write(128 + countBaos.size());
            countBaos.close();
            baos.write(countBaos.toByteArray());
        }
        else {
            baos.write(nodeSize);
        }
        StringBuffer bits = new StringBuffer();
        String endNodeLocation = findHuffPath(stringParent, END_NODE);
        for (PathNode pn : pathNodes) {
            for (NodePair np : pn.getChildren()) {
                bits.append(findHuffPath(stringParent, np.getName()));
                bits.append(findHuffPath(pathNodeParent, np.getConnection()));
            }
            bits.append(endNodeLocation);
            while (bits.length() >= 8) {
                int next = 0;
                for (int i = 0; i < 8; i++) {
                    next = (byte) next << 1;
                    if (bits.charAt(i) == '1') {
                        next++;
                    }
                }
                baos.write(next);
                bits.delete(0, 8);
            }
        }

        if (bits.length() > 0) {
            int next = 0;
            for (int i = 0;  i < 8; i++) {
                next = (byte) next << 1;
                if (i < bits.length() && bits.charAt(i) == '1') {
                    next++;
                }
            }
            baos.write(next);
        }
        byte[] result = baos.toByteArray();
        if (treeDebug) {
            ByteArrayInputStream bais = new ByteArrayInputStream(result);
            int value = bais.read();
            while (value != -1) {
                log.debug(String.valueOf(value));
                value = bais.read();
            }
        }
        baos.close();
        return result;
    }

    private byte[] toByteArray(int value) {
        return new byte[] {
            (byte) (value >> 24),
            (byte) (value >> 16),
            (byte) (value >> 8),
            (byte) value};
    }

    public String findHuffPath(HuffNode trie, Object need) {
        HuffNode left = trie.getLeft();
        HuffNode right = trie.getRight();
        if (left != null && left.getValue() != null) {
            if (need.equals(left.getValue())) {
                return "0";
            }
        }
        if (right != null && right.getValue() != null) {
            if (need.equals(right.getValue())) {
                return "1";
            }
        }
        if (left != null) {
            String leftPath = findHuffPath(left, need);
            if (leftPath.length() > 0) {
                return "0" + leftPath;
            }
        }
        if (right != null) {
            String rightPath = findHuffPath(right, need);
            if (rightPath.length() > 0) {
                return "1" + rightPath;
            }
        }
        return "";
    }

    public static String toJson(Object anObject) {
        String output = "";
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        try {
            output = mapper.writeValueAsString(anObject);
        }
        catch (Exception e) {
            log.error("Could no serialize the object to json " + anObject, e);
        }
        return output;
    }

    private byte[] byteProcess(List<String> entries)
        throws IOException, UnsupportedEncodingException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeflaterOutputStream dos = new DeflaterOutputStream(baos,
            new Deflater(Deflater.BEST_COMPRESSION));
        for (String segment : entries) {
            dos.write(segment.getBytes("UTF-8"));
            dos.write("\0".getBytes("UTF-8"));
        }
        dos.finish();
        dos.close();
        return baos.toByteArray();
    }

    private List<HuffNode> getStringNodeList(List<String> pathStrings) {
        List<HuffNode> nodes = new ArrayList<HuffNode>();
        int idx = 1;
        for (String part : pathStrings) {
            nodes.add(new HuffNode(part, idx++));
        }
        nodes.add(new HuffNode(END_NODE, idx));
        return nodes;
    }

    private List<HuffNode> getPathNodeNodeList(List<PathNode> pathNodes) {
        List<HuffNode> nodes = new ArrayList<HuffNode>();
        int idx = 0;
        for (PathNode pn : pathNodes) {
            nodes.add(new HuffNode(pn, idx++));
        }
        return nodes;
    }

    public HuffNode makeTrie(List<HuffNode> nodesList) {
        // drop the first node if path node value, it is not needed
        if (nodesList.get(0).getValue() instanceof PathNode) {
            nodesList.remove(0);
        }
        while (nodesList.size() > 1) {
            int node1 = findSmallest(-1, nodesList);
            int node2 = findSmallest(node1, nodesList);
            HuffNode hn1 = nodesList.get(node1);
            HuffNode hn2 = nodesList.get(node2);
            HuffNode merged = mergeNodes(hn1, hn2);
            nodesList.remove(hn1);
            nodesList.remove(hn2);
            nodesList.add(merged);
        }
        if (treeDebug) {
            printTrie(nodesList.get(0), 0);
        }
        return nodesList.get(0);
    }

    private int findSmallest(int exclude, List<HuffNode> nodes) {
        int smallest = -1;
        for (int index = 0; index < nodes.size(); index++) {
            if (index == exclude) { continue; }
            if (smallest == -1 || nodes.get(index).getWeight() <
                nodes.get(smallest).getWeight()) {
                smallest = index;
            }
        }
        return smallest;
    }

    private HuffNode mergeNodes(HuffNode node1, HuffNode node2) {
        HuffNode left = node1;
        HuffNode right = node2;
        HuffNode parent = new HuffNode(null, left.weight + right.weight, left, right);
        return parent;
    }

    public List<String> hydrateContentPackage(byte[] payload)
        throws IOException, UnsupportedEncodingException {
        List<HuffNode> pathDictionary = new ArrayList<HuffNode>();
        List<HuffNode> nodeDictionary = new ArrayList<HuffNode>();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Inflater i = new Inflater();
        InflaterOutputStream ios = new InflaterOutputStream(baos, i);
        ios.write(payload);
        ios.finish();
        long read = i.getBytesRead();

        String name = "";
        int weight = 1;
        for (byte b : baos.toByteArray()) {
            if (b == '\0') {
                pathDictionary.add(new HuffNode(name, weight++));
                name = "";
            }
            else {
                name += (char) b;
            }
        }
        pathDictionary.add(new HuffNode(END_NODE, weight));
        List<HuffNode> triePathDictionary = new ArrayList<HuffNode>();
        triePathDictionary.addAll(pathDictionary);
        HuffNode pathTrie = makeTrie(triePathDictionary);

        StringBuffer nodeBits = new StringBuffer();
        ByteArrayInputStream bais = new ByteArrayInputStream(payload, (int) read,
            (int) (payload.length - read));

        int value = bais.read();
        // check for size bits
        int nodeCount = value;
        if (value > 127) {
            byte[] count = new byte[value - 128];
            bais.read(count);
            int total = 0;
            for (int k = 0; k < value - 128; k++) {
                total = (total << 8) | (count[k] & 0xFF);
            }
            nodeCount = total;
        }
        value = bais.read();
        while (value != -1) {
            String someBits = Integer.toString(value, 2);
            for (int pad = 0; pad < 8 - someBits.length(); pad++) {
                nodeBits.append("0");
            }
            nodeBits.append(someBits);
            value = bais.read();
        }
        for (int j = 0; j < nodeCount; j++) {
            nodeDictionary.add(new HuffNode(new PathNode(), j));
        }
        List<HuffNode> trieNodeDictionary = new ArrayList<HuffNode>();
        trieNodeDictionary.addAll(nodeDictionary);
        HuffNode nodeTrie = makeTrie(trieNodeDictionary);

        // populate the PathNodes so we can rebuild the cool url tree
        Set<PathNode> pathNodes =  populatePathNodes(nodeDictionary,
            pathTrie, nodeTrie, nodeBits);
        // find the root, he has no parents
        PathNode root = null;
        for (PathNode pn : pathNodes) {
            if (pn.getParents().size() == 0) {
                root = pn;
                break;
            }
        }
        // time to make the doughnuts
        List<String> urls = new ArrayList<String>();
        StringBuffer aPath = new StringBuffer();
        makeURLs(root, urls, aPath);
        return urls;
    }

    public Object findHuffNodeValueByBits(HuffNode trie, String bits) {
        HuffNode left = trie.getLeft();
        HuffNode right = trie.getRight();

        if (bits.length() == 0) {
            return trie.getValue();
        }

        char bit = bits.charAt(0);
        if (bit == '0') {
            if (left == null) { throw new RuntimeException("Encoded path not in trie"); }
            return findHuffNodeValueByBits(left, bits.substring(1));
        }
        else if (bit == '1') {
            if (right == null) { throw new RuntimeException("Encoded path not in trie"); }
            return findHuffNodeValueByBits(right, bits.substring(1));
        }
        return null;
    }

    private Set<PathNode> populatePathNodes(List<HuffNode> nodeDictionary,
        HuffNode pathTrie, HuffNode nodeTrie, StringBuffer nodeBits) {
        Set<PathNode> pathNodes = new HashSet<PathNode>();
        for (HuffNode node : nodeDictionary) {
            pathNodes.add((PathNode) node.getValue());
            boolean stillNode = true;
            while (stillNode) {
                // get first child name
                // if its END_NODE we are done
                String nameValue = null;
                StringBuffer nameBits = new StringBuffer();
                while (nameValue == null && stillNode) {
                    nameBits.append(nodeBits.charAt(0));
                    nodeBits.deleteCharAt(0);
                    Object lookupValue = findHuffNodeValueByBits(pathTrie,
                        nameBits.toString());
                    if (lookupValue != null) {
                        if (lookupValue.equals(END_NODE)) {
                            stillNode = false;
                            break;
                        }
                        nameValue = (String) lookupValue;
                    }
                    if (nodeBits.length() == 0) {
                        stillNode = false;
                    }
                }

                PathNode nodeValue = null;
                StringBuffer pathBits = new StringBuffer();
                while (nodeValue == null && stillNode) {
                    pathBits.append(nodeBits.charAt(0));
                    nodeBits.deleteCharAt(0);
                    PathNode lookupValue = (PathNode) findHuffNodeValueByBits(nodeTrie,
                        pathBits.toString());
                    if (lookupValue != null) {
                        nodeValue = lookupValue;
                        nodeValue.addParent((PathNode) node.getValue());
                        ((PathNode) node.getValue()).addChild(
                            new NodePair(nameValue, nodeValue));
                    }
                    if (nodeBits.length() == 0) {
                        stillNode = false;
                    }
                }
            }
        }
        return pathNodes;
    }

    private void makeURLs(PathNode root, List<String> urls, StringBuffer aPath) {
        if (root == null) {
            // if no PathNode, we just bail. No need to cause an NPE.
            return;
        }
        if (root.getChildren().size() == 0) {
            urls.add(aPath.toString());
        }
        for (NodePair child : root.getChildren()) {
            StringBuffer childPath = new StringBuffer(aPath.substring(0));
            childPath.append("/");
            childPath.append(child.getName());
            makeURLs(child.getConnection(), urls, childPath);
        }
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

    /**
     *
     * HuffNode
     */
    public class HuffNode {
        private long id = 0;
        private Object value = null;
        private int weight = 0;
        private HuffNode left = null;
        private HuffNode right = null;

        public HuffNode(Object value, int weight, HuffNode left, HuffNode right) {
            this.value = value;
            this.weight = weight;
            this.left = left;
            this.right = right;
            this.id = huffNodeId++;
        }
        public HuffNode(Object value, int weight) {
            this.value = value;
            this.weight = weight;
            this.id = huffNodeId++;
        }

        public Object getValue() {
            return this.value;
        }

        public int getWeight() {
            return this.weight;
        }

        public HuffNode getLeft() {
            return this.left;
        }

        public HuffNode getRight() {
            return this.right;
        }

        long getId() {
            return this.id;
        }

        public String toString() {
            return "ID: " + id +
                   ", Value: " + value +
                   ", Weight: " + weight +
                   ", Left: " + left +
                   ", Right: " + right;
        }
    }

    /**
     *
     * PathNode
     */

    public class PathNode {
        private long id = 0;
        private List<NodePair> children = new ArrayList<NodePair>();
        private List<PathNode> parents = new ArrayList<PathNode>();

        public PathNode() {
            this.id = pathNodeId++;
        }

        public long getId() {
            return id;
        }

        void addChild(NodePair cp) {
            this.children.add(cp);
        }

        void addParent(PathNode cp) {
            if (!parents.contains(cp)) {
                this.parents.add(cp);
            }
        }

        public List<NodePair> getChildren() {
            Collections.sort(this.children);
            return this.children;
        }

        List<PathNode> getParents() {
            return this.parents;
        }

        void setParents(List<PathNode> parents) {
            this.parents = parents;
        }

        void addParents(List<PathNode> parents) {
            for (PathNode pn : parents) {
                addParent(pn);
            }
        }

        boolean isEquivalentTo(PathNode that) {
            if (this.getId() == that.getId()) {
                return true;
            }
            // same number of children with the same names for child nodes
            if (this.getChildren().size() != that.getChildren().size()) {
                return false;
            }
            for (NodePair thisnp : this.getChildren()) {
                boolean found = false;
                for (NodePair thatnp : that.getChildren()) {
                    if (thisnp.getName().equals(thatnp.getName())) {
                        if (thisnp.getConnection().isEquivalentTo(thatnp.getConnection())) {
                            found = true;
                            break;
                        }
                        else {
                            return false;
                        }
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }

        public String toString() {
            StringBuffer parentList = new StringBuffer("ID: ");
            parentList.append(id).append(", Parents");
            for (PathNode parent : parents) {
                parentList.append(": ").append(parent.getId());
            }

            // "ID: " + id + ", Parents" + parentList + ", Children: " + children;
            return parentList.append(", Children: ").append(children).toString();
        }
    }

    /**
     *
     * NodePair
     */

    public static class NodePair implements Comparable{
        private String name;
        private PathNode connection;

        NodePair(String name, PathNode connection) {
            this.name = name;
            this.connection = connection;
        }

        public String getName() {
            return name;
        }

        public PathNode getConnection() {
            return connection;
        }

        void setConnection(PathNode connection) {
            this.connection = connection;
        }

        public String toString() {
            return "Name: " + name + ", Connection: " + connection.getId();
        }

        /* (non-Javadoc)
         * @see java.lang.Comparable#compareTo(java.lang.Object)
         */
        @Override
        public int compareTo(Object other) {
            return this.name.compareTo(((NodePair) other).name);
        }

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (!(other instanceof NodePair)) {
                return false;
            }

            return this.name.equals(((NodePair) other).getName());
        }

        public int hashCode() {
            return name.hashCode();
        }
    }
}
