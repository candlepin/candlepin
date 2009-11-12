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
package com.redhat.rhn.common.cert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * XmlTag a simple class to render an XML tag.
 *
 * Borrowed from project Spacewalk: http://spacewalk.redhat.com
 */
public class XmlTag {

    private String tag;
    private Map<String, String> attribs;
    private List<Object> body;
    private boolean spaceBeforeEndTag;
    private List<String> keys;

    /**
     * Standard xml header with utf-8 encoding. Example usage:<br />
     * <code>
     * StringBuffer buf = new StringBuffer();
     * buf.append(XmlTag.XML_HDR_UTF8);
     * buf.append(new XmlTag("foo").render());
     * </code>
     */
    public static final String XML_HDR_UTF8 = "<?xml version=\"1.0\" encoding=\"utf-8\"?>";

    /**
     * Standard xml header with no encoding. Example usage:<br />
     * <code>
     * StringBuffer buf = new StringBuffer();
     * buf.append(XmlTag.XML_HDR);
     * buf.append(new XmlTag("foo").render());
     * </code>
     */
    public static final String XML_HDR = "<?xml version=\"1.0\"?>";

    /**
     * Public constructor
     * @param tagIn the name of the tag
     */
    protected XmlTag(String tagIn) {
        this(tagIn, true);
    }

    /**
     * Constructs an XmlTag. The <code>spaceBefore</code> attribute controls
     * whether a space is inserted before the closing tag of a single line
     * element.<br />
     * For example, a true value for spaceBefore and a tagIn of "foo" will
     * render &lt;foo /&gt;. A spaceBefore value of false would've rendered
     * &lt;foo/&gt;.
     * @param tagIn the name of the tag
     * @param spaceBefore true if you want a space before the closing tag.
     */

    protected XmlTag(String tagIn, boolean spaceBefore) {
        attribs = new HashMap<String, String>();
        tag = tagIn;
        body = new ArrayList<Object>();
        keys = new ArrayList<String>();
        spaceBeforeEndTag = spaceBefore;
    }

    /**
     * set an attribute of the html tag
     * @param name the attribute name
     * @param value the attribute value
     */
    public void setAttribute(String name, String value) {
        attribs.put(name, value);
        keys.add(name);
    }

    /**
     * Removes an attribute of the html tag.
     * @param name the attribute name to be removed.
     */
    public void removeAttribute(String name) {
        attribs.remove(name);
        keys.remove(name);
    }

    /**
     * set the body of the tag
     * @param bodyIn the new body
     */
    public void addBody(String bodyIn) {
        body.add(bodyIn);
    }

    /**
     * Adds the given tag to the body of this tag.
     * @param bodyTag Tag to be added to the body of this tag.
     */
    public void addBody(XmlTag bodyTag) {
        body.add(bodyTag);
    }

    /**
     * render the tag into a string
     * @return the string version
     */
    public String render() {
        StringBuffer ret = new StringBuffer();
        ret.append(renderOpenTag());
        if (!hasBody()) {
            ret.deleteCharAt(ret.length() - 1);
            if (spaceBeforeEndTag) {
                ret.append(" />");
            }
            else {
                ret.append("/>");
            }
        }
        else {
            ret.append(renderBody());
            ret.append(renderCloseTag());
        }
        return ret.toString();
    }

    /**
     * render the open tag and attributes
     * @return the open tag as a string
     */
    public String renderOpenTag() {
        StringBuffer ret = new StringBuffer("<");
        ret.append(tag);

        for (String attrib : keys) {
            ret.append(" ");
            ret.append(attrib);
            ret.append("=\"");
            ret.append(attribs.get(attrib));
            ret.append("\"");
        }
        ret.append(">");

        return ret.toString();
    }

    /**
     * render the tag body
     * @return the tag body as a string
     */
    public String renderBody() {
        StringBuffer buf = new StringBuffer();
        
        for (Object obj : body) {
            buf.append(convertToString(obj));
        }

        return buf.toString();
    }

    private String convertToString(Object o) {
        if (o instanceof XmlTag) {
            return ((XmlTag) o).render();
        }
        else if (o instanceof String) {
            return (String) o;
        }
        else {
            return o.toString();
        }
    }

    /**
     * render the close tag
     * @return the close tag as a string
     */
    public String renderCloseTag() {
        return "</" + tag + ">";
    }

    /**
     * Returns true if this tag has a body defined.
     * @return true if this tag has a body defined.
     */
    public boolean hasBody() {
        return (body.size() > 0);
    }
}
