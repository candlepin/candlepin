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
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.apache.log4j.Logger;
import org.candlepin.config.Config;
import org.candlepin.json.model.Content;
import org.candlepin.json.model.EntitlementBody;
import org.candlepin.json.model.Order;
import org.candlepin.json.model.Service;
import org.candlepin.json.model.Subscription;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509ExtensionWrapper;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import com.google.common.collect.Collections2;
import com.google.inject.Inject;

/**
 * X509ExtensionUtil
 */
public class X509V2ExtensionUtil extends X509Util{

    private static Logger log = Logger.getLogger(X509V2ExtensionUtil.class);
    private Config config;
    private EntitlementCurator entCurator;
    private String thisVersion = "2.0";

    private static long nodeId = 0;

    @Inject
    public X509V2ExtensionUtil(Config config, EntitlementCurator entCurator) {
        // Output everything in UTC
        this.config = config;
        this.entCurator = entCurator;
    }

    public Set<X509ExtensionWrapper> getExtensions(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent,
        org.candlepin.model.Subscription sub) {
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
        Map<String, EnvironmentContent> promotedContent,
        org.candlepin.model.Subscription sub) throws IOException {
        Set<X509ByteExtensionWrapper> toReturn =
            new LinkedHashSet<X509ByteExtensionWrapper>();

        EntitlementBody eb = createEntitlementBodyContent(products, ent,
            contentPrefix, promotedContent, sub);

        List<Content> contentList = getContentList(eb);
        PathNode treeRoot = makePathTree(contentList, new PathNode());
        List<String> nodeStrings = orderStrings(treeRoot);
        byte[] pathDictionary = byteProcess(nodeStrings);
        List<PathNode> orderedNodes = orderNodes(treeRoot);
        byte[] nodeDictionary = makeNodeDictionary(nodeStrings, orderedNodes);
        byte[] value = combineByteArrays(pathDictionary, nodeDictionary);
        X509ByteExtensionWrapper bodyExtension =
            new X509ByteExtensionWrapper(OIDUtil.REDHAT_OID + "." +
                OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ENTITLEMENT_DATA_KEY),
                false, value);
        toReturn.add(bodyExtension);

        return toReturn;
    }

    public EntitlementBody createEntitlementBody(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent,
        org.candlepin.model.Subscription sub) {

        EntitlementBody toReturn = new EntitlementBody();
        toReturn.setConsumer(ent.getConsumer().getUuid());
        toReturn.setQuantity(ent.getQuantity());
        toReturn.setSubscription(createSubscription(sub, ent));
        toReturn.setOrder(createOrder(sub));
        toReturn.setProducts(createProducts(products, contentPrefix, promotedContent,
            ent.getConsumer(), ent));

        return toReturn;
    }

    public EntitlementBody createEntitlementBodyContent(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent,
        org.candlepin.model.Subscription sub) {

        EntitlementBody toReturn = new EntitlementBody();
        toReturn.setProducts(createProducts(products, contentPrefix, promotedContent,
            ent.getConsumer(), ent));

        return toReturn;
    }

    public Subscription createSubscription(
        org.candlepin.model.Subscription sub, Entitlement ent) {
        Subscription toReturn = new Subscription();
        toReturn.setSku(sub.getProduct().getId().toString());
        toReturn.setName(sub.getProduct().getName());

        String warningPeriod = sub.getProduct().getAttributeValue(
            "warning_period");
        if (warningPeriod != null && !warningPeriod.trim().equals("")) {
            // only included if not the default value of 0
            if (!warningPeriod.equals("0")) {
                toReturn.setWarning(new Integer(warningPeriod));
            }
        }

        String socketLimit = sub.getProduct().getAttributeValue("sockets");
        if (socketLimit != null && !socketLimit.trim().equals("")) {
            toReturn.setSockets(new Integer(socketLimit));
        }

        String management = sub.getProduct().getAttributeValue("management_enabled");
        if (management != null && !management.trim().equals("")) {
            // only included if not the default value of false
            Boolean m = new Boolean(management.equalsIgnoreCase("true") ||
                management.equalsIgnoreCase("1"));
            if (m) {
                toReturn.setManagement(m);
            }
        }

        String stackingId = sub.getProduct().getAttributeValue("stacking_id");
        if (stackingId != null && !stackingId.trim().equals("")) {
            toReturn.setStackingId(stackingId);
        }

        String virtOnly = ent.getPool().getAttributeValue("virt_only");
        if (virtOnly != null && !virtOnly.trim().equals("")) {
            // only included if not the default value of false
            Boolean vo = new Boolean(virtOnly.equalsIgnoreCase("true") ||
                virtOnly.equalsIgnoreCase("1"));
            if (vo) {
                toReturn.setVirtOnly(vo);
            }
        }

        toReturn.setService(createService(sub));
        return toReturn;
    }

    private Service createService(org.candlepin.model.Subscription sub) {
        if (sub.getProduct().getAttributeValue("support_level") == null &&
            sub.getProduct().getAttributeValue("support_type") == null) {
            return null;
        }
        Service toReturn = new Service();
        toReturn.setLevel(sub.getProduct().getAttributeValue("support_level"));
        toReturn.setType(sub.getProduct().getAttributeValue("support_type"));

        return toReturn;
    }

    public Order createOrder(org.candlepin.model.Subscription sub) {
        SimpleDateFormat iso8601DateFormat = Util.getUTCDateFormat();
        Order toReturn = new Order();

        toReturn.setNumber(sub.getId().toString());
        toReturn.setQuantity(sub.getQuantity());
        toReturn.setStart(iso8601DateFormat.format(sub.getStartDate()));
        toReturn.setEnd(iso8601DateFormat.format(sub.getEndDate()));

        if (sub.getContractNumber() != null &&
            !sub.getContractNumber().trim().equals("")) {
            toReturn.setContract(sub.getContractNumber());
        }

        if (sub.getAccountNumber() != null &&
            !sub.getAccountNumber().trim().equals("")) {
            toReturn.setAccount(sub.getAccountNumber());
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

    private org.candlepin.json.model.Product mapProduct(Product product,
        String contentPrefix, Map<String, EnvironmentContent> promotedContent,
        Consumer consumer, Entitlement ent) {

        org.candlepin.json.model.Product toReturn = new org.candlepin.json.model.Product();

        toReturn.setId(product.getId());
        toReturn.setName(product.getName());

        String version = product.hasAttribute("version") ?
            product.getAttributeValue("version") : "";
        toReturn.setVersion(version);

        String arch = product.hasAttribute("arch") ?
            product.getAttributeValue("arch") : "";
        StringTokenizer st = new StringTokenizer(arch, ",");
        List<String> archList = new ArrayList<String>();
        while (st.hasMoreElements()) {
            archList.add((String) st.nextElement());
        }
        toReturn.setArchitectures(archList);

        toReturn.setContent(createContent(filterProductContent(product, ent),
            contentPrefix, promotedContent, consumer));

        return toReturn;
    }

    public List<Content> createContent(
        Set<ProductContent> productContent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent, Consumer consumer) {

        List<Content> toReturn = new ArrayList<Content>();

        boolean enableEnvironmentFiltering = config.environmentFileringEnabled();

        for (ProductContent pc : productContent) {
            Content content = new Content();
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

            content.setId(pc.getContent().getId());
            content.setType(pc.getContent().getType());
            content.setName(pc.getContent().getName());
            content.setLabel(pc.getContent().getLabel());
            content.setVendor(pc.getContent().getVendor());
            content.setPath(contentPath);
            content.setGpgUrl(pc.getContent().getGpgUrl());

            // Check if we should override the enabled flag due to setting on promoted
            // content:
            Boolean enabled = pc.getEnabled();
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
                content.setEnabled(enabled);
            }

            // Include metadata expiry if specified on the content:
            if (pc.getContent().getMetadataExpire() != null) {
                content.setMetadataExpire(pc.getContent().getMetadataExpire());
            }

            // Include required tags if specified on the content set:
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

    private PathNode makePathTree(List<Content> contents, PathNode parent) {
        PathNode endMarker = new PathNode();
        for (Content c : contents) {
            String path = c.getPath();
            log.debug(path);
            StringTokenizer st = new StringTokenizer(path, "/");
            makePathForURL(st, parent, endMarker);
        }
        if (log.isDebugEnabled()) { printTree(parent, 0); }
        condenseSubTreeNodes(endMarker);
        if (log.isDebugEnabled()) { printTree(parent, 0); }
        return parent;
    }

    private void printTree(PathNode pn, int tab) {
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
        log.debug(nodeRep);
        for (NodePair cp : pn.getChildren()) {
            printTree(cp.getConnection(), tab + 1);
        }
    }
    private void makePathForURL(StringTokenizer st, PathNode parent, PathNode endMarker) {
        if (st.hasMoreTokens()) {
            String childVal = st.nextToken();
            boolean isNew = true;
            for (NodePair child : parent.getChildren()) {
                if (child.getName().equals(childVal)) {
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
                    PathNode oneParent = merged.getParents().get(0);
                    for (NodePair child : oneParent.getChildren()) {
                        if (child.getConnection().getId() == merged.getId()) {
                            name = child.getName();
                            break;
                        }
                    }

                    // copy grandparents to merged parent node.
                    List<PathNode> movingParents = toRemove.getParents();
                    merged.addParents(movingParents);

                    // all grandparents with name now point to merged node
                    for (PathNode pn : merged.getParents()) {
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

    private List<String> orderStrings(PathNode parent) throws IOException {
        List<String> parts = new ArrayList<String>();
        // walk tree to make string map
        Map<String, Integer> segments =  new HashMap<String, Integer>();
        Set<PathNode> nodes =  new HashSet<PathNode>();
        buildSegments(segments, nodes, parent);
        for (String part : segments.keySet()) {
            if (!part.equals("")) {
                int count = segments.get(part);
                if (parts.size() == 0) {
                    parts.add(part);
                }
                else {
                    int pos = parts.size();
                    for (int i = 0; i < parts.size(); i++) {
                        if (count <= segments.get(parts.get(i))) {
                            pos = i;
                            break;
                        }
                    }
                    parts.add(pos, part);
                }
            }
        }
        parts.add("::");
        if (log.isDebugEnabled()) {
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
                    count = new Integer(0);
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
        if (log.isDebugEnabled()) {
            log.debug(result);
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

    private byte[] makeNodeDictionary(List<String> nodeStrings,
        List<PathNode> orderedNodes)
        throws UnsupportedEncodingException, IOException {
        String delimiter = nodeStrings.get(nodeStrings.size() - 1);
        List<String> result = new ArrayList<String>();

        // hash for lookups
        Map<String, Integer> stringHash = new HashMap<String, Integer>();
        int index = 0;
        for (String s : nodeStrings) {
            stringHash.put(s, index++);
        }
        Map<Long, Integer> nodeHash = new HashMap<Long, Integer>();
        index = 0;
        for (PathNode pn : orderedNodes) {
            nodeHash.put(pn.getId(), index++);
        }

        for (PathNode pn : orderedNodes) {
            for (NodePair np : pn.getChildren()) {
                result.add(stringHash.get(np.getName()).toString());
                result.add(nodeHash.get(np.getConnection().getId()).toString());
            }
            result.add(delimiter);
        }
        if (log.isDebugEnabled()) {
            log.debug(result);
        }
        return byteProcess(result);
    }

    public static String toJson(Object anObject) {
        String output = "";
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
        mapper.setVisibility(JsonMethod.FIELD, Visibility.ANY);
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
            new Deflater(Deflater.HUFFMAN_ONLY));
        for (String segment : entries) {
            dos.write(segment.getBytes("UTF-8"));
        }
        dos.finish();
        dos.close();
        return baos.toByteArray();
    }

    private byte[] combineByteArrays(byte[] a, byte[] b) {
        byte[] combined = new byte[a.length + b.length];
        for (int i = 0; i < combined.length - 1; ++i) {
            combined[i] = i < a.length ? a[i] : b[i - a.length];
        }
        return combined;
    }

    private class PathNode {
        private long id = 0;
        private List<NodePair> children = new ArrayList<NodePair>();
        private List<PathNode> parents = new ArrayList<PathNode>();

        PathNode() {
            this.id = nodeId++;
        }

        long getId() {
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

        List<NodePair> getChildren() {
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
            // same number of children with the same names for child nodes
            if (this.getChildren().size() != that.getChildren().size()) {
                return false;
            }
            for (NodePair thisnp : this.getChildren()) {
                boolean found = false;
                for (NodePair thatnp : that.getChildren()) {
                    if (thisnp.getName().equals(thatnp.getName())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }

        public String toString() {
            String parentList =  "";
            for (PathNode parent : parents) {
                parentList += ": " + parent.getId();
            }
            parentList += "";
            return "ID: " + id + ", Parents" + parentList + ", Children: " + children;
        }
    }

    private class NodePair {
        private String name;
        private PathNode connection;

        NodePair(String name, PathNode connection) {
            this.name = name;
            this.connection = connection;
        }

        String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }

        PathNode getConnection() {
            return connection;
        }

        void setConnection(PathNode connection) {
            this.connection = connection;
        }

        public String toString() {
            return "Name: " + name + ", Connection: " + connection.getId();
        }
    }
}
