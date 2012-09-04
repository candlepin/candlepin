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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
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
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

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
    private String thisVersion = "3.0";

    private long pathNodeId = 0;
    private long huffNodeId = 0;
    private static final String END_NODE = "*";

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

        X509ByteExtensionWrapper bodyExtension =
            new X509ByteExtensionWrapper(OIDUtil.REDHAT_OID + "." +
                OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ENTITLEMENT_DATA_KEY),
                false, retreiveContentValue(eb));
        toReturn.add(bodyExtension);

        return toReturn;
    }

    public byte[] createEntitlementDataPayload(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent,
        org.candlepin.model.Subscription sub)
        throws UnsupportedEncodingException, IOException {

        EntitlementBody map = createEntitlementBody(products, ent,
            contentPrefix, promotedContent, sub);

        String json = toJson(map);
        return processPayload(json);
    }


    private byte[] retreiveContentValue(EntitlementBody eb) throws IOException {
        List<Content> contentList = getContentList(eb);
        PathNode treeRoot = makePathTree(contentList, new PathNode());
        List<String> nodeStrings = orderStrings(treeRoot);
        List<HuffNode> stringHuffNodes = getStringNodeList(nodeStrings);
        HuffNode stringTrieParent = makeTrie(stringHuffNodes);
        byte[] pathDictionary = byteProcess(nodeStrings);
        List<PathNode> orderedNodes = orderNodes(treeRoot);
        List<HuffNode> pathNodeHuffNodes = getPathNodeNodeList(orderedNodes);
        HuffNode pathNodeTrieParent = makeTrie(pathNodeHuffNodes);
        byte[] nodeDictionary = makeNodeDictionary(stringTrieParent,
            pathNodeTrieParent, orderedNodes);
        byte[] value = combineByteArrays(pathDictionary, nodeDictionary);
        return value;
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

    private void printTrie(HuffNode hn, int tab) {
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

        log.debug(nodeRep + "\n");
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
                        if (count < segments.get(parts.get(i))) {
                            pos = i;
                            break;
                        }
                    }
                    parts.add(pos, part);
                }
            }
        }
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

    private byte[] makeNodeDictionary(HuffNode stringParent,
        HuffNode pathNodeParent, List<PathNode> pathNodes)
        throws UnsupportedEncodingException, IOException {

        byte[] result = new byte[3];
        int nodeSize = pathNodes.size();
        if (nodeSize < 128) {
            result[0] = (byte) nodeSize;
            result = new byte[] {result[0]};
        }
        else {
            result[0] = (byte) 130;
            byte[] count = BigInteger.valueOf(nodeSize).toByteArray();
            int resultIdx = 1;
            for (byte b : count) {
                result[resultIdx++] = b;
            }
        }
        StringBuffer bits = new StringBuffer();
        String endNodeLocation = findNodeString(stringParent, END_NODE);
        for (PathNode pn : pathNodes) {
            for (NodePair np : pn.getChildren()) {
                bits.append(findNodeString(stringParent, np.getName()));
                bits.append(findPathNode(pathNodeParent, np.getConnection()));
            }
            bits.append(endNodeLocation);
        }
        if (log.isDebugEnabled()) {
            log.debug(bits);
        }
        if (bits.length() % 8 != 0) {
            int toAdd = 8 - (bits.length() % 8);
            for (int i = 0;  i < toAdd; i++) {
                bits.append("0");
            }
        }
        while (bits.length() > 0) {
            String oneByte = bits.substring(0, 8);
            result = combineByteArrays(result,
                new byte[] {(byte) Integer.parseInt(oneByte, 2)});
            bits.delete(0, 8);
        }
        return result;
    }

    private String findNodeString(HuffNode stringTrie, String need) {
        HuffNode left = stringTrie.getLeft();
        HuffNode right = stringTrie.getRight();
        if (left != null && left.getValue() != null) {
            String value = (String) left.getValue();
            if (value.equals(need)) {
                return "0";
            }
        }
        if (right != null && right.getValue() != null) {
            String value = (String) right.getValue();
            if (value.equals(need)) {
                return "1";
            }
        }
        if (left != null) {
            String leftPath = findNodeString(left, need);
            if (leftPath.length() > 0) {
                return "0" + leftPath;
            }
        }
        if (right != null) {
            String rightPath = findNodeString(right, need);
            if (rightPath.length() > 0) {
                return "1" + rightPath;
            }
        }
        return "";
    }

    private String findPathNode(HuffNode pathNodeTrie, PathNode need) {
        HuffNode left = pathNodeTrie.getLeft();
        HuffNode right = pathNodeTrie.getRight();
        if (left != null &&
            left.getValue() != null) {
            PathNode value = (PathNode) left.getValue();
            if (value.getId() == need.getId()) {
                return "0";
            }
        }
        if (right != null &&
            right.getValue() != null) {
            PathNode value = (PathNode) right.getValue();
            if (value.getId() == need.getId()) {
                return "1";
            }
        }
        if (left != null) {
            String leftPath = findPathNode(left, need);
            if (leftPath.length() > 0) {
                return "0" + leftPath;
            }
        }
        if (right != null) {
            String rightPath = findPathNode(right, need);
            if (rightPath.length() > 0) {
                return "1" + rightPath;
            }
        }
        return "";
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
            new Deflater(Deflater.BEST_COMPRESSION));
        for (String segment : entries) {
            dos.write(segment.getBytes("UTF-8"));
            dos.write("\0".getBytes("UTF-8"));
        }
        dos.finish();
        dos.close();
        return baos.toByteArray();
    }

    public byte[] combineByteArrays(byte[] a, byte[] b) {
        if (a.length == 0) { return b; }
        if (b.length == 0) { return a; }
        byte[] combined = new byte[a.length + b.length];
        for (int i = 0; i < combined.length; ++i) {
            combined[i] = i < a.length ? a[i] : b[i - a.length];
        }
        return combined;
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
        int idx = 1;
        for (PathNode pn : pathNodes) {
            nodes.add(new HuffNode(pn, idx++));
        }
        return nodes;
    }

    private HuffNode makeTrie(List<HuffNode> nodesList) {
        while (nodesList.size() > 1) {
            HuffNode node1 = findSmallest(null, nodesList);
            HuffNode node2 = findSmallest(node1, nodesList);
            HuffNode parent = mergeNodes(node1, node2);
            nodesList.add(parent);
            nodesList.remove(node1);
            nodesList.remove(node2);
        }
        printTrie(nodesList.get(0), 0);
        return nodesList.get(0);
    }

    private HuffNode findSmallest(HuffNode exclude, List<HuffNode> nodes) {
        HuffNode smallest = null;
        for (HuffNode n : nodes) {
            boolean isExclude = false;
            if (exclude != null) {
                isExclude = n.getId() == exclude.getId();
            }
            if (!isExclude && (smallest == null || n.getWeight() < smallest.getWeight())) {
                smallest = n;
            }
        }
        return smallest;
    }

    private HuffNode mergeNodes(HuffNode node1, HuffNode node2) {
        HuffNode left = node1.weight <= node2.weight ? node1 : node2;
        HuffNode right = node1.weight > node2.weight ? node1 : node2;
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
        ByteArrayInputStream bais = new ByteArrayInputStream(payload,
            (new Long(read)).intValue(), (new Long(payload.length - read).intValue()));
        int value = bais.read();
        while (value != -1) {
            String someBits = Integer.toString(value, 2);
            for (int pad = 0; pad < 8 - someBits.length(); pad++) {
                nodeBits.append("0");
            }
            nodeBits.append(someBits);
            value = bais.read();
        }
        // check for size bits
        int nodeCount = 0;
        if (nodeBits.substring(0, 8).equals("10000010")) {
            nodeCount = Integer.parseInt(nodeBits.substring(8, 24), 2);
            nodeBits.delete(0, 24);
        }
        else {
            nodeCount = Integer.parseInt(nodeBits.substring(0, 8), 2);
            nodeBits.delete(0, 8);
        }
        for (int j = 1; j <= nodeCount; j++) {
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

    private Object findHuffNodeValueByBits(HuffNode trie, String bits) {
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
                    String lookupValue = (String) findHuffNodeValueByBits(pathTrie,
                        nameBits.toString());
                    if (lookupValue != null) {
                        if (lookupValue.equals(END_NODE)) {
                            stillNode = false;
                            break;
                        }
                        nameValue = lookupValue;
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

    private class HuffNode {
        private long id = 0;
        private Object value = null;
        private int weight = 0;
        private HuffNode left = null;
        private HuffNode right = null;

        HuffNode(Object value, int weight, HuffNode left, HuffNode right) {
            this.value = value;
            this.weight = weight;
            this.left = left;
            this.right = right;
            this.id = huffNodeId++;
        }
        HuffNode(Object value, int weight) {
            this.value = value;
            this.weight = weight;
            this.id = huffNodeId++;
        }

        Object getValue() {
            return this.value;
        }

        int getWeight() {
            return this.weight;
        }

        HuffNode getLeft() {
            return this.left;
        }

        HuffNode getRight() {
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

    private class PathNode {
        private long id = 0;
        private List<NodePair> children = new ArrayList<NodePair>();
        private List<PathNode> parents = new ArrayList<PathNode>();

        PathNode() {
            this.id = pathNodeId++;
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
