/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.bridge;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.batik.dom.svg.SVGOMDocument;
import org.apache.batik.dom.util.XLinkSupport;
import org.apache.batik.dom.util.XMLSupport;

import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.gvt.GraphicsNodeRenderContext;

import org.apache.batik.parser.AWTTransformProducer;
import org.apache.batik.parser.LengthHandler;
import org.apache.batik.parser.LengthParser;
import org.apache.batik.parser.ParseException;
import org.apache.batik.parser.PreserveAspectRatioHandler;
import org.apache.batik.parser.PreserveAspectRatioParser;
import org.apache.batik.parser.PreserveAspectRatioParser;

import org.apache.batik.util.SVGConstants;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.svg.SVGDocument;
import org.w3c.dom.svg.SVGElement;
import org.w3c.dom.svg.SVGLangSpace;
import org.w3c.dom.svg.SVGLength;
import org.w3c.dom.svg.SVGNumberList;
import org.w3c.dom.svg.SVGPreserveAspectRatio;

/**
 * A collection of utility methods for SVG.
 *
 * @author <a href="mailto:tkormann@apache.org">Thierry Kormann</a>
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 * @version $Id$
 */
public abstract class SVGUtilities implements SVGConstants, ErrorConstants {

    /**
     * No instance of this class is required.
     */
    protected SVGUtilities() {}

    /////////////////////////////////////////////////////////////////////////
    // common methods
    /////////////////////////////////////////////////////////////////////////

    /**
     * Converts an SVGNumberList into a float array.
     * @param l the list to convert
     */
    public static float[] convertSVGNumberList(SVGNumberList l) {
        int n = l.getNumberOfItems();
        if (n == 0) {
            return null;
        }
        float fl[] = new float[n];
        for (int i=0; i < n; i++) {
            fl[i] = l.getItem(i).getValue();
        }
        return fl;
    }

    /**
     * Converts a string into a float.
     * @param s the float representation to convert
     */
    public static float convertSVGNumber(String s) {
        return Float.parseFloat(s);
    }

    /**
     * Converts a string into an integer.
     * @param s the integer representation to convert
     */
    public static int convertSVGInteger(String s) {
        return Integer.parseInt(s);
    }

    /**
     * Converts the specified ratio to float number.
     * @param v the ratio value to convert
     * @exception NumberFormatException if the ratio is not a valid
     * number or percentage
     */
    public static float convertRatio(String v) {
        float d = 1;
        if (v.endsWith("%")) {
            v = v.substring(0, v.length() - 1);
            d = 100;
        }
        float r = Float.parseFloat(v)/d;
        if (r < 0) {
            r = 0;
        } else if (r > 1) {
            r = 1;
        }
        return r;
    }

    /**
     * Returns the content of the 'desc' child of the given element.
     */
    public static String getDescription(SVGElement elt) {
        String result = "";
        boolean preserve = false;
        Node n = elt.getFirstChild();
        if (n != null && n.getNodeType() == Node.ELEMENT_NODE) {
            String name =
                (n.getPrefix() == null) ? n.getNodeName() : n.getLocalName();
            if (name.equals(SVG_DESC_TAG)) {
                preserve = ((SVGLangSpace)n).getXMLspace().equals
                    (SVG_PRESERVE_VALUE);
                for (n = n.getFirstChild(); n != null; n = n.getNextSibling()) {
                    if (n.getNodeType() == Node.TEXT_NODE) {
                        result += n.getNodeValue();
                    }
                }
            }
        }
        return (preserve)
            ? XMLSupport.preserveXMLSpace(result)
            : XMLSupport.defaultXMLSpace(result);
    }

    /**
     * Tests whether or not the given element match a specified user agent.
     *
     * @param elt the element to check
     * @param ua the user agent
     */
    public static boolean matchUserAgent(Element elt, UserAgent ua) {
        test: if (elt.hasAttributeNS(null, SVG_SYSTEM_LANGUAGE_ATTRIBUTE)) {
            // Tests the system languages.
            String sl = elt.getAttributeNS(null, SVG_SYSTEM_LANGUAGE_ATTRIBUTE);
            StringTokenizer st = new StringTokenizer(sl, ",");
            while (st.hasMoreTokens()) {
                String s = st.nextToken();
                if (matchUserLanguage(s, ua.getLanguages())) {
                    break test;
                }
            }
            return false;
        }
        if (elt.hasAttributeNS(null, SVG_REQUIRED_FEATURES_ATTRIBUTE)) {
            // Tests the system features.
            String sf = elt.getAttributeNS(null, SVG_REQUIRED_FEATURES_ATTRIBUTE);
            StringTokenizer st = new StringTokenizer(sf, " ");
            while (st.hasMoreTokens()) {
                String s = st.nextToken();
                if (!ua.hasFeature(s)) {
                    return false;
                }
            }
        }
        if (elt.hasAttributeNS(null, SVG_REQUIRED_EXTENSIONS_ATTRIBUTE)) {
            // Tests the system features.
            String sf = elt.getAttributeNS(null, SVG_REQUIRED_EXTENSIONS_ATTRIBUTE);
            StringTokenizer st = new StringTokenizer(sf, " ");
            while (st.hasMoreTokens()) {
                String s = st.nextToken();
                if (!ua.supportExtension(s)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Tests whether or not the specified language specification matches
     * the user preferences.
     *
     * @param s the langage to check
     * @param userLanguages the user langages
     */
    protected static boolean matchUserLanguage(String s, String userLanguages) {
        StringTokenizer st = new StringTokenizer(userLanguages, ",");
        while (st.hasMoreTokens()) {
            String t = st.nextToken();
            if (t.startsWith(s)) {
                if (t.length() > s.length()) {
                    return (t.charAt(s.length()) == '-') ? true : false;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the value of the specified attribute specified on the
     * specified element or one of its ancestor. Ancestors are found
     * using the xlink:href attribute.
     *
     * @param element the element to start with
     * @param namespaceURI the namespace URI of the attribute to return
     * @param attrName the name of the attribute to search
     * @param ctx the bridge context
     * @return the value of the attribute or an empty string if not defined
     */
    public static String getChainableAttributeNS(Element element,
                                                 String namespaceURI,
                                                 String attrName,
                                                 BridgeContext ctx) {

        DocumentLoader loader = ctx.getDocumentLoader();
        Element e = element;
        List refs = new LinkedList();
        for (;;) {
            String v = e.getAttributeNS(namespaceURI, attrName);
            if (v.length() > 0) { // exit if attribute defined
                return v;
            }
            String uriStr = XLinkSupport.getXLinkHref(e);
            if (uriStr.length() == 0) { // exit if no more xlink:href
                return "";
            }
            SVGDocument svgDoc = (SVGDocument)e.getOwnerDocument();
            URL baseURL = ((SVGOMDocument)svgDoc).getURLObject();
            try {
                URL url = new URL(baseURL, uriStr);
                Iterator iter = refs.iterator();
                while (iter.hasNext()) {
                    URL urlTmp = (URL)iter.next();
                    if (urlTmp.sameFile(url) &&
                        urlTmp.getRef().equals(url.getRef())) {
                        throw new BridgeException
                            (e, ERR_XLINK_HREF_CIRCULAR_DEPENDENCIES,
                             new Object[] {uriStr});
                    }
                }
                URIResolver resolver = new URIResolver(svgDoc, loader);
                e = resolver.getElement(url.toString());
                refs.add(url);
            } catch(MalformedURLException ex) {
                throw new BridgeException(e, ERR_URI_MALFORMED,
                                          new Object[] {uriStr});
            } catch(IOException ex) {
                throw new BridgeException(e, ERR_URI_IO,
                                          new Object[] {uriStr});
            }
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // <linearGradient> and <radialGradient>
    /////////////////////////////////////////////////////////////////////////

    /**
     * Returns a Point2D in user units according to the specified parameters.
     *
     * @param xStr the x coordinate
     * @param xAttr the name of the attribute that represents the x coordinate
     * @param yStr the y coordinate
     * @param yAttr the name of the attribute that represents the y coordinate
     * @param unitsType the coordinate system (OBJECT_BOUNDING_BOX |
     * USER_SPACE_ON_USE)
     * @param uctx the unit processor context
     */
    public static Point2D convertPoint(String xStr,
                                       String xAttr,
                                       String yStr,
                                       String yAttr,
                                       short unitsType,
                                       UnitProcessor.Context uctx) {
        float x, y;
        switch (unitsType) {
        case OBJECT_BOUNDING_BOX:
            x = UnitProcessor.svgHorizontalCoordinateToObjectBoundingBox
                (xStr, xAttr, uctx);
            y = UnitProcessor.svgVerticalCoordinateToObjectBoundingBox
                (yStr, yAttr, uctx);
            break;
        case USER_SPACE_ON_USE:
            x = UnitProcessor.svgHorizontalCoordinateToUserSpace
                (xStr, xAttr, uctx);
            y = UnitProcessor.svgVerticalCoordinateToUserSpace
                (yStr, yAttr, uctx);
            break;
        default:
            throw new Error(); // can't be reached
        }
        return new Point2D.Float(x, y);
    }

    /**
     * Returns a float in user units according to the specified parameters.
     *
     * @param length the length
     * @param attr the name of the attribute that represents the length
     * @param unitsType the coordinate system (OBJECT_BOUNDING_BOX |
     * USER_SPACE_ON_USE)
     * @param uctx the unit processor context
     */
    public static float convertLength(String length,
                                      String attr,
                                      short unitsType,
                                      UnitProcessor.Context uctx) {
        switch (unitsType) {
        case OBJECT_BOUNDING_BOX:
            return  UnitProcessor.svgOtherLengthToObjectBoundingBox
                (length, attr, uctx);
        case USER_SPACE_ON_USE:
            return UnitProcessor.svgOtherLengthToUserSpace(length, attr, uctx);
        default:
            throw new Error(); // can't be reached
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // <mask> region
    /////////////////////////////////////////////////////////////////////////

    /**
     * Returns the mask region according to the x, y, width, height,
     * and maskUnits attributes.
     *
     * @param maskElement the mask element that defines the various attributes
     * @param maskedElement the element referencing the mask
     * @param maskedNode the graphics node to mask (objectBoundingBox)
     * @param ctx the bridge context
     */
    public static Rectangle2D convertMaskRegion(Element maskElement,
                                                Element maskedElement,
                                                GraphicsNode maskedNode,
                                                BridgeContext ctx) {

        // 'x' attribute - default is -10%
        String xStr = maskElement.getAttributeNS(null, SVG_X_ATTRIBUTE);
        if (xStr.length() == 0) {
            xStr = SVG_MASK_X_DEFAULT_VALUE;
        }
        // 'y' attribute - default is -10%
        String yStr = maskElement.getAttributeNS(null, SVG_Y_ATTRIBUTE);
        if (yStr.length() == 0) {
            yStr = SVG_MASK_Y_DEFAULT_VALUE;
        }
        // 'width' attribute - default is 120%
        String wStr = maskElement.getAttributeNS(null, SVG_WIDTH_ATTRIBUTE);
        if (wStr.length() == 0) {
            wStr = SVG_MASK_WIDTH_DEFAULT_VALUE;
        }
        // 'height' attribute - default is 120%
        String hStr = maskElement.getAttributeNS(null, SVG_HEIGHT_ATTRIBUTE);
        if (hStr.length() == 0) {
            hStr = SVG_MASK_HEIGHT_DEFAULT_VALUE;
        }
        // 'maskUnits' attribute - default is 'objectBoundingBox'
        short unitsType;
        String units =
            maskElement.getAttributeNS(null, SVG_MASK_UNITS_ATTRIBUTE);
        if (units.length() == 0) {
            unitsType = OBJECT_BOUNDING_BOX;
        } else {
            unitsType = parseCoordinateSystem
                (maskElement, SVG_MASK_UNITS_ATTRIBUTE, units);
        }

        // resolve units in the (referenced) maskedElement's coordinate system
        UnitProcessor.Context uctx
            = UnitProcessor.createContext(ctx, maskedElement);

        return convertRegion(xStr,
                             yStr,
                             wStr,
                             hStr,
                             unitsType,
                             maskedNode,
                             uctx,
                             ctx.getGraphicsNodeRenderContext());
    }

    /////////////////////////////////////////////////////////////////////////
    // <pattern> region
    /////////////////////////////////////////////////////////////////////////

    /**
     * Returns the pattern region according to the x, y, width, height,
     * and patternUnits attributes.
     *
     * @param patternElement the pattern element that defines the attributes
     * @param paintedElement the element referencing the pattern
     * @param paintedNode the graphics node to paint (objectBoundingBox)
     * @param ctx the bridge context
     */
    public static Rectangle2D convertPatternRegion(Element patternElement,
                                                   Element paintedElement,
                                                   GraphicsNode paintedNode,
                                                   BridgeContext ctx) {

        // 'x' attribute - default is 0%
        String xStr = getChainableAttributeNS
            (patternElement, null, SVG_X_ATTRIBUTE, ctx);
        if (xStr.length() == 0) {
            xStr = SVG_PATTERN_X_DEFAULT_VALUE;
        }
        // 'y' attribute - default is 0%
        String yStr = getChainableAttributeNS
            (patternElement, null, SVG_Y_ATTRIBUTE, ctx);
        if (yStr.length() == 0) {
            yStr = SVG_PATTERN_Y_DEFAULT_VALUE;
        }
        // 'width' attribute - required
        String wStr = getChainableAttributeNS
            (patternElement, null, SVG_WIDTH_ATTRIBUTE, ctx);
        if (wStr.length() == 0) {
            throw new BridgeException(patternElement, ERR_ATTRIBUTE_MISSING,
                                      new Object[] {SVG_WIDTH_ATTRIBUTE});
        }
        // 'height' attribute - required
        String hStr = getChainableAttributeNS
            (patternElement, null, SVG_HEIGHT_ATTRIBUTE, ctx);
        if (hStr.length() == 0) {
            throw new BridgeException(patternElement, ERR_ATTRIBUTE_MISSING,
                                      new Object[] {SVG_HEIGHT_ATTRIBUTE});
        }
        // 'patternUnits' attribute - default is 'objectBoundingBox'
        short unitsType;
        String units = getChainableAttributeNS
            (patternElement, null, SVG_PATTERN_UNITS_ATTRIBUTE, ctx);
        if (units.length() == 0) {
            unitsType = OBJECT_BOUNDING_BOX;
        } else {
            unitsType = parseCoordinateSystem
                (patternElement, SVG_PATTERN_UNITS_ATTRIBUTE, units);
        }

        // resolve units in the (referenced) paintedElement's coordinate system
        UnitProcessor.Context uctx
            = UnitProcessor.createContext(ctx, paintedElement);

        return convertRegion(xStr,
                             yStr,
                             wStr,
                             hStr,
                             unitsType,
                             paintedNode,
                             uctx,
                             ctx.getGraphicsNodeRenderContext());
    }

    /////////////////////////////////////////////////////////////////////////
    // <filter> and filter primitive
    /////////////////////////////////////////////////////////////////////////

    /**
     * Returns an array of 2 float numbers that describes the filter
     * resolution of the specified filter element.
     *
     * @param filterElement the filter element
     * @param ctx the bridge context
     */
    public static
        float [] convertFilterRes(Element filterElement, BridgeContext ctx) {

        float [] filterRes = new float[2];
        String s = getChainableAttributeNS
            (filterElement, null, SVG_FILTER_RES_ATTRIBUTE, ctx);
        if (s.length() == 0) {
            filterRes[0] = -1;
            filterRes[1] = -1;

        } else {
            try {
                filterRes[0] = -1; // -1 means unspecified
                StringTokenizer tokens = new StringTokenizer(s, " ");
                filterRes[0] = Float.parseFloat(tokens.nextToken());
                if (tokens.hasMoreTokens()) {
                    filterRes[1] = Float.parseFloat(tokens.nextToken());
                } else {
                    // if only one value is specified, resY = resX
                    filterRes[1] = filterRes[0];
                }
                if (tokens.hasMoreTokens()) {
                    throw new BridgeException
                        (filterElement, ERR_ATTRIBUTE_VALUE_MALFORMED,
                         new Object[] {SVG_FILTER_RES_ATTRIBUTE, s});
                }
            } catch (NumberFormatException ex) {
                throw new BridgeException
                    (filterElement, ERR_ATTRIBUTE_VALUE_MALFORMED,
                     new Object[] {SVG_FILTER_RES_ATTRIBUTE, s, ex});
            }
            if (filterRes[0] < 0 || filterRes[1] < 0) {
                throw new BridgeException
                    (filterElement, ERR_ATTRIBUTE_VALUE_MALFORMED,
                     new Object[] {SVG_FILTER_RES_ATTRIBUTE, s});
            }
        }
        return filterRes;
    }

    /**
     * Returns the filter region according to the x, y, width, height,
     * and filterUnits attributes.
     *
     * @param filterElement the filter element that defines the attributes
     * @param filteredElement the element referencing the filter
     * @param filteredNode the graphics node to filter (objectBoundingBox)
     * @param uctx the unit processor context (userSpaceOnUse)
     * @param ctx the bridge context
     */
    public static
        Rectangle2D convertFilterChainRegion(Element filterElement,
                                             Element filteredElement,
                                             GraphicsNode filteredNode,
                                             BridgeContext ctx) {

        // 'x' attribute - default is -10%
        String xStr = getChainableAttributeNS
            (filterElement, null, SVG_X_ATTRIBUTE, ctx);
        if (xStr.length() == 0) {
            xStr = SVG_FILTER_X_DEFAULT_VALUE;
        }
        // 'y' attribute - default is -10%
        String yStr = getChainableAttributeNS
            (filterElement, null, SVG_Y_ATTRIBUTE, ctx);
        if (yStr.length() == 0) {
            yStr = SVG_FILTER_Y_DEFAULT_VALUE;
        }
        // 'width' attribute - default is 120%
        String wStr = getChainableAttributeNS
            (filterElement, null, SVG_WIDTH_ATTRIBUTE, ctx);
        if (wStr.length() == 0) {
            wStr = SVG_FILTER_WIDTH_DEFAULT_VALUE;
        }
        // 'height' attribute - default is 120%
        String hStr = getChainableAttributeNS
            (filterElement, null, SVG_HEIGHT_ATTRIBUTE, ctx);
        if (hStr.length() == 0) {
            hStr = SVG_FILTER_HEIGHT_DEFAULT_VALUE;
        }
        // 'filterUnits' attribute - default is 'objectBoundingBox'
        short unitsType;
        String units = getChainableAttributeNS
            (filterElement, null, SVG_FILTER_UNITS_ATTRIBUTE, ctx);
        if (units.length() == 0) {
            unitsType = OBJECT_BOUNDING_BOX;
        } else {
            unitsType = parseCoordinateSystem
                (filterElement, SVG_FILTER_UNITS_ATTRIBUTE, units);
        }

        // resolve units in the (referenced) filteredElement's coordinate system
        UnitProcessor.Context uctx
            = UnitProcessor.createContext(ctx, filteredElement);

        return convertRegion(xStr,
                             yStr,
                             wStr,
                             hStr,
                             unitsType,
                             filteredNode,
                             uctx,
                             ctx.getGraphicsNodeRenderContext());
    }

    /**
     * Returns the filter primitive region according to the x, y,
     * width, height, and filterUnits attributes. Processing the
     * element as the top one in the filter chain.
     *
     * @param filterPrimitiveElement the filter primitive element
     * @param filteredElement the element referencing the filter
     * @param filteredNode the graphics node to use (objectBoundingBox)
     * @param defaultRegion the default region to filter
     * @param filterRegion the filter chain region
     * @param ctx the bridge context
     */
    public static
        Rectangle2D convertFilterPrimitiveRegion(Element filterPrimitiveElement,
                                                 Element filteredElement,
                                                 GraphicsNode filteredNode,
                                                 Rectangle2D defaultRegion,
                                                 Rectangle2D filterRegion,
                                                 BridgeContext ctx) {

        // 'primitiveUnits' - default is userSpaceOnUse
        Node parentNode = filterPrimitiveElement.getParentNode();
        String units = "";
        if ((parentNode != null) &&
            (parentNode.getNodeType() == parentNode.ELEMENT_NODE)) {
            Element parent = (Element)parentNode;
            units = getChainableAttributeNS(parent,
                                            null,
                                            SVG_PRIMITIVE_UNITS_ATTRIBUTE,
                                            ctx);
        }
        short unitsType;
        if (units.length() == 0) {
            unitsType = USER_SPACE_ON_USE;
        } else {
            unitsType = parseCoordinateSystem
                (filterPrimitiveElement, SVG_FILTER_UNITS_ATTRIBUTE, units);
        }

        // 'x' attribute - default is defaultRegion.getX()
        String xStr =
            filterPrimitiveElement.getAttributeNS(null, SVG_X_ATTRIBUTE);

        // 'y' attribute - default is defaultRegion.getY()
        String yStr =
            filterPrimitiveElement.getAttributeNS(null, SVG_Y_ATTRIBUTE);

        // 'width' attribute - default is defaultRegion.getWidth()
        String wStr =
            filterPrimitiveElement.getAttributeNS(null, SVG_WIDTH_ATTRIBUTE);

        // 'height' attribute - default is defaultRegion.getHeight()
        String hStr =
            filterPrimitiveElement.getAttributeNS(null, SVG_HEIGHT_ATTRIBUTE);

        double x = defaultRegion.getX();
        double y = defaultRegion.getY();
        double w = defaultRegion.getWidth();
        double h = defaultRegion.getHeight();

        // resolve units in the (referenced) filteredElement's coordinate system
        UnitProcessor.Context uctx
            = UnitProcessor.createContext(ctx, filteredElement);

        switch (unitsType) {
        case OBJECT_BOUNDING_BOX:
            GraphicsNodeRenderContext rc = ctx.getGraphicsNodeRenderContext();
            Rectangle2D bounds = filteredNode.getGeometryBounds(rc);
            if (xStr.length() != 0) {
                x = UnitProcessor.svgHorizontalCoordinateToObjectBoundingBox
                    (xStr, SVG_X_ATTRIBUTE, uctx);
                x = bounds.getX() + x*bounds.getWidth();
            }
            if (yStr.length() != 0) {
                y = UnitProcessor.svgVerticalCoordinateToObjectBoundingBox
                    (yStr, SVG_Y_ATTRIBUTE, uctx);
                y = bounds.getY() + y*bounds.getHeight();
            }
            if (wStr.length() != 0) {
                w = UnitProcessor.svgHorizontalLengthToObjectBoundingBox
                    (wStr, SVG_WIDTH_ATTRIBUTE, uctx);
                w *= bounds.getWidth();
            }
            if (hStr.length() != 0) {
                h = UnitProcessor.svgVerticalLengthToObjectBoundingBox
                    (hStr, SVG_HEIGHT_ATTRIBUTE, uctx);
                h *= bounds.getHeight();
            }
            break;
        case USER_SPACE_ON_USE:
            if (xStr.length() != 0) {
                x = UnitProcessor.svgHorizontalCoordinateToUserSpace
                    (xStr, SVG_X_ATTRIBUTE, uctx);
            }
            if (yStr.length() != 0) {
                y = UnitProcessor.svgVerticalCoordinateToUserSpace
                    (yStr, SVG_Y_ATTRIBUTE, uctx);
            }
            if (wStr.length() != 0) {
                w = UnitProcessor.svgHorizontalLengthToUserSpace
                    (wStr, SVG_WIDTH_ATTRIBUTE, uctx);
            }
            if (hStr.length() != 0) {
                h = UnitProcessor.svgVerticalLengthToUserSpace
                    (hStr, SVG_HEIGHT_ATTRIBUTE, uctx);
            }
            break;
        default:
            throw new Error(); // can't be reached
        }

        Rectangle2D region = new Rectangle2D.Double(x, y, w, h);
        region.intersect(region, filterRegion, region);
        return region;
    }

    /////////////////////////////////////////////////////////////////////////
    // region convenient methods
    /////////////////////////////////////////////////////////////////////////


    /** The userSpaceOnUse coordinate system constants. */
    public static final short USER_SPACE_ON_USE = 1;

    /** The objectBoundingBox coordinate system constants. */
    public static final short OBJECT_BOUNDING_BOX = 2;

    /** The strokeWidth coordinate system constants. */
    public static final short STROKE_WIDTH = 3;

    /**
     * Parses the specified coordinate system defined by the specified element.
     *
     * @param e the element that defines the coordinate system
     * @param attr the attribute which contains the coordinate system
     * @param coordinateSystem the coordinate system to parse
     * @return OBJECT_BOUNDING_BOX | USER_SPACE_ON_USE
     */
    public static short parseCoordinateSystem(Element e,
                                              String attr,
                                              String coordinateSystem) {
        if (SVG_USER_SPACE_ON_USE_VALUE.equals(coordinateSystem)) {
            return USER_SPACE_ON_USE;
        } else if (SVG_OBJECT_BOUNDING_BOX_VALUE.equals(coordinateSystem)) {
            return OBJECT_BOUNDING_BOX;
        } else {
            throw new BridgeException(e, ERR_ATTRIBUTE_VALUE_MALFORMED,
                                      new Object[] {attr, coordinateSystem});
        }
    }

    /**
     * Parses the specified coordinate system defined by the specified
     * marker element.
     *
     * @param e the element that defines the coordinate system
     * @param attr the attribute which contains the coordinate system
     * @param coordinateSystem the coordinate system to parse
     * @return STROKE_WIDTH | USER_SPACE_ON_USE
     */
    public static short parseMarkerCoordinateSystem(Element e,
                                                    String attr,
                                                    String coordinateSystem) {
        if (SVG_USER_SPACE_ON_USE_VALUE.equals(coordinateSystem)) {
            return USER_SPACE_ON_USE;
        } else if (SVG_STROKE_WIDTH_VALUE.equals(coordinateSystem)) {
            return STROKE_WIDTH;
        } else {
            throw new BridgeException(e, ERR_ATTRIBUTE_VALUE_MALFORMED,
                                      new Object[] {attr, coordinateSystem});
        }
    }

    /**
     * Returns a rectangle that represents the region defined by the
     * specified coordinates.
     *
     * @param xStr the x coordinate of the region
     * @param yStr the y coordinate of the region
     * @param wStr the width of the region
     * @param hStr the height of the region
     * @param targetNode the graphics node (needed for objectBoundingBox)
     * @param uctx the unit processor context (needed for userSpaceOnUse)
     * @param rc the graphics node render context
     */
    protected static Rectangle2D convertRegion(String xStr,
                                               String yStr,
                                               String wStr,
                                               String hStr,
                                               short unitsType,
                                               GraphicsNode targetNode,
                                               UnitProcessor.Context uctx,
                                               GraphicsNodeRenderContext rc) {

        // construct the mask region in the appropriate coordinate system
        double x, y, w, h;
        switch (unitsType) {
        case OBJECT_BOUNDING_BOX:
            x = UnitProcessor.svgHorizontalCoordinateToObjectBoundingBox
                (xStr, SVG_X_ATTRIBUTE, uctx);
            y = UnitProcessor.svgVerticalCoordinateToObjectBoundingBox
                (yStr, SVG_Y_ATTRIBUTE, uctx);
            w = UnitProcessor.svgHorizontalLengthToObjectBoundingBox
                (wStr, SVG_WIDTH_ATTRIBUTE, uctx);
            h = UnitProcessor.svgVerticalLengthToObjectBoundingBox
                (hStr, SVG_HEIGHT_ATTRIBUTE, uctx);

            Rectangle2D bounds = targetNode.getGeometryBounds(rc);
            if (bounds != null ) {
                x = bounds.getX() + x*bounds.getWidth();
                y = bounds.getY() + y*bounds.getHeight();
                w *= bounds.getWidth();
                h *= bounds.getHeight();
            } else {
                x = y = w = h = 0;
            }
            break;
        case USER_SPACE_ON_USE:
            x = UnitProcessor.svgHorizontalCoordinateToUserSpace
                (xStr, SVG_X_ATTRIBUTE, uctx);
            y = UnitProcessor.svgVerticalCoordinateToUserSpace
                (yStr, SVG_Y_ATTRIBUTE, uctx);
            w = UnitProcessor.svgHorizontalLengthToUserSpace
                (wStr, SVG_WIDTH_ATTRIBUTE, uctx);
            h = UnitProcessor.svgVerticalLengthToUserSpace
                (hStr, SVG_HEIGHT_ATTRIBUTE, uctx);
            break;
        default:
            throw new Error(); // can't be reached
        }
        return new Rectangle2D.Double(x, y, w, h);
    }

    /////////////////////////////////////////////////////////////////////////
    // coordinate system and transformation support methods
    /////////////////////////////////////////////////////////////////////////

    /**
     * Returns an AffineTransform according to the specified parameters.
     *
     * @param e the element that defines the transform
     * @param attr the name of the attribute that represents the transform
     * @param transform the transform to parse
     *
     */
    public static AffineTransform convertTransform(Element e,
                                                   String attr,
                                                   String transform) {
        try {
            StringReader r = new StringReader(transform);
            return AWTTransformProducer.createAffineTransform(r);
        } catch (ParseException ex) {
            throw new BridgeException(e, ERR_ATTRIBUTE_VALUE_MALFORMED,
                                      new Object[] {attr, transform, ex});
        }
    }

    /**
     * Returns an AffineTransform to move to the objectBoundingBox
     * coordinate system.
     *
     * @param Tx the original transformation
     * @param node the graphics node that defines the coordinate
     *             system to move into
     * @param rc the graphics node render context
     */
    public static AffineTransform toObjectBBox(AffineTransform Tx,
                                               GraphicsNode node,
                                               GraphicsNodeRenderContext rc) {

        AffineTransform Mx = new AffineTransform();
        Rectangle2D bounds = node.getGeometryBounds(rc);
        Mx.translate(bounds.getX(), bounds.getY());
        Mx.scale(bounds.getWidth(), bounds.getHeight());
        Mx.concatenate(Tx);
        return Mx;
    }

    /**
     * Returns the specified a Rectangle2D move to the objectBoundingBox
     * coordinate system of the specified graphics node.
     *
     * @param r the original Rectangle2D
     * @param node the graphics node that defines the coordinate
     *             system to move into
     * @param rc the graphics node render context
     */
    public static Rectangle2D toObjectBBox(Rectangle2D r,
                                           GraphicsNode node,
                                           GraphicsNodeRenderContext rc) {

        Rectangle2D bounds = node.getGeometryBounds(rc);
        if(bounds != null){
            return new Rectangle2D.Double
                (bounds.getX() + r.getX()*bounds.getWidth(),
                 bounds.getY() + r.getY()*bounds.getHeight(),
                 r.getWidth() * bounds.getWidth(),
                 r.getHeight() * bounds.getHeight());
        } else {
            return new Rectangle2D.Double();
        }
    }
}
