/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.bridge;

import java.awt.Dimension;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;

import org.apache.batik.ext.awt.image.renderable.ClipRable8Bit;
import org.apache.batik.ext.awt.image.renderable.Filter;
import org.apache.batik.gvt.CanvasGraphicsNode;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.gvt.GraphicsNodeRenderContext;
import org.apache.batik.gvt.filter.GraphicsNodeRable8Bit;
import org.apache.batik.parser.ParseException;
import org.apache.batik.util.SVGConstants;

import org.w3c.dom.Element;
import org.w3c.dom.svg.SVGElement;
import org.w3c.dom.svg.SVGSVGElement;

/**
 * Bridge class for the &lt;svg> element.
 *
 * @author <a href="mailto:tkormann@apache.org">Thierry Kormann</a>
 * @version $Id$
 */
public class SVGSVGElementBridge implements GraphicsNodeBridge,
                                            SVGConstants,
                                            ErrorConstants {

    /**
     * Constructs a new bridge for the &lt;svg> element.
     */
    public SVGSVGElementBridge() {}

    /**
     * Creates a <tt>GraphicsNode</tt> according to the specified parameters.
     *
     * @param ctx the bridge context to use
     * @param e the element that describes the graphics node to build
     * @return a graphics node that represents the specified element
     */
    public GraphicsNode createGraphicsNode(BridgeContext ctx, Element e) {
        CanvasGraphicsNode gn = new CanvasGraphicsNode();

        UnitProcessor.Context uctx = UnitProcessor.createContext(ctx, e);
        String s;

        boolean isOutermost = (((SVGElement)e).getOwnerSVGElement() == null);
        float x = 0;
        float y = 0;
        // x and y have no meaning on the outermost 'svg' element
        if (!isOutermost) {
            // 'x' attribute - default is 0
            s = e.getAttributeNS(null, SVG_X_ATTRIBUTE);
            if (s.length() != 0) {
                x = UnitProcessor.svgHorizontalCoordinateToUserSpace
                    (s, SVG_X_ATTRIBUTE, uctx);
            }
            // 'y' attribute - default is 0
            s = e.getAttributeNS(null, SVG_Y_ATTRIBUTE);
            if (s.length() != 0) {
                y = UnitProcessor.svgVerticalCoordinateToUserSpace
                    (s, SVG_Y_ATTRIBUTE, uctx);
            }
        }

        // 'width' attribute - default is 100%
        s = e.getAttributeNS(null, SVG_WIDTH_ATTRIBUTE);
        if (s.length() == 0) {
            s = "100%";
        }
        float w = UnitProcessor.svgHorizontalLengthToUserSpace
            (s, SVG_WIDTH_ATTRIBUTE, uctx);

        // 'height' attribute - default is 100%
        s = e.getAttributeNS(null, SVG_HEIGHT_ATTRIBUTE);
        if (s.length() == 0) {
            s = "100%";
        }
        float h = UnitProcessor.svgVerticalLengthToUserSpace
            (s, SVG_HEIGHT_ATTRIBUTE, uctx);

        // 'visibility'
        gn.setVisible(CSSUtilities.convertVisibility(e));

        // 'viewBox' and "preserveAspectRatio' attributes
        AffineTransform at =
            ViewBox.getPreserveAspectRatioTransform(e, w, h);

        float actualWidth = w;
        float actualHeight = h;
        try {
            AffineTransform atInv = at.createInverse();
            actualWidth = (float) (w*atInv.getScaleX());
            actualHeight = (float) (h*atInv.getScaleY());
        } catch (NoninvertibleTransformException ex) {}

        at.preConcatenate(AffineTransform.getTranslateInstance(x, y));

        // 'overflow' and 'clip'
        // The outermost preserveAspectRatio matrix is set by the user
        // agent, so we don't need to set the transform for outermost svg
        Shape clip = null;
        if (!isOutermost) {
            gn.setTransform(at);
            if (CSSUtilities.convertOverflow(e)) { // overflow:hidden
                float [] offsets = CSSUtilities.convertClip(e);
                if (offsets == null) { // clip:auto
                    clip = new Rectangle2D.Float(x, y, w, h);
                } else { // clip:rect(<x> <y> <w> <h>)
                    // offsets[0] = top
                    // offsets[1] = right
                    // offsets[2] = bottom
                    // offsets[3] = left
                    clip = new Rectangle2D.Float(x+offsets[3],
                                                 y+offsets[0],
                                                 w-offsets[1],
                                                 h-offsets[2]);
                }
            }
        } else {
            ctx.setDocumentSize(new Dimension((int)w, (int)h));
            clip = new Rectangle2D.Float(x, y, w, h);
        }

        if (clip != null) {
            try {
                at = at.createInverse(); // clip in user space
                clip = at.createTransformedShape(clip);
                Filter filter = new GraphicsNodeRable8Bit
                    (gn, ctx.getGraphicsNodeRenderContext());
                gn.setClip(new ClipRable8Bit(filter, clip));
            } catch (NoninvertibleTransformException ex) {}
        }

        ctx.openViewport
            (e, new SVGSVGElementViewport((SVGSVGElement)e,
                                          actualWidth,
                                          actualHeight));
        return gn;
    }

    /**
     * Performs an update according to the specified event.
     *
     * @param evt the event describing the update to perform
     */
    public void update(BridgeMutationEvent evt) {
        throw new Error("Not implemented");
    }

    /**
     * Builds using the specified BridgeContext and element, the
     * specified graphics node.
     *
     * @param ctx the bridge context to use
     * @param e the element that describes the graphics node to build
     * @param node the graphics node to build
     */
    public void buildGraphicsNode(BridgeContext ctx,
                                  Element e,
                                  GraphicsNode node) {
        // <!> FIXME: no clip, filter, mask or opacity ???

        // we have built all children, we can close the viewport
        ctx.closeViewport(e);

        // bind the specified element and its associated graphics node if needed
        if (ctx.isDynamic()) {
            ctx.bind(e, node);
        }
    }

    /**
     * Returns true as the &lt;svg> element is a container.
     */
    public boolean isComposite() {
        return true;
    }


    /**
     * A viewport for a SVGSVGElement.
     */
    public static class SVGSVGElementViewport implements Viewport {

        private SVGSVGElement e;
        private float width;
        private float height;

        /**
         * Constructs a new viewport with the specified <tt>SVGSVGElement</tt>.
         * @param e the SVGSVGElement that defines this viewport
         * @param w the width of the viewport
         * @param h the height of the viewport
         */
        public SVGSVGElementViewport(SVGSVGElement e, float w, float h) {
            this.e = e;
            this.width = w;
            this.height = h;
        }

        /**
         * Returns the width of this viewport.
         */
        public float getWidth(){
            return width;
        }

        /**
         * Returns the height of this viewport.
         */
        public float getHeight(){
            return height;
        }
    }
}
