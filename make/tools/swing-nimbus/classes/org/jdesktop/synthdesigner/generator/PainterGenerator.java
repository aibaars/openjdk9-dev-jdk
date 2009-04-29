/*
 * Copyright 2002-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package org.jdesktop.synthdesigner.generator;

import org.jdesktop.swingx.designer.BezierControlPoint;
import org.jdesktop.swingx.designer.Canvas;
import org.jdesktop.swingx.designer.EllipseShape;
import org.jdesktop.swingx.designer.Layer;
import org.jdesktop.swingx.designer.PaintedShape;
import org.jdesktop.swingx.designer.PathShape;
import org.jdesktop.swingx.designer.RectangleShape;
import org.jdesktop.swingx.designer.SimpleShape;
import org.jdesktop.swingx.designer.TemplateLayer;
import org.jdesktop.swingx.designer.paint.Gradient;
import org.jdesktop.swingx.designer.paint.Matte;
import org.jdesktop.swingx.designer.paint.PaintModel;
import org.jdesktop.swingx.designer.paint.RadialGradient;
import org.jdesktop.swingx.designer.paint.GradientStop;
import org.jdesktop.swingx.designer.paint.AbstractGradient;
import static org.jdesktop.synthdesigner.generator.GeneratorUtils.makePretty;
import static org.jdesktop.synthdesigner.generator.GeneratorUtils.toClassName;
import static org.jdesktop.synthdesigner.generator.GeneratorUtils.toConstantName;
import static org.jdesktop.synthdesigner.generator.TemplateWriter.read;
import static org.jdesktop.synthdesigner.generator.TemplateWriter.writeSrcFile;
import org.jdesktop.synthdesigner.synthmodel.UIIconRegion;
import org.jdesktop.synthdesigner.synthmodel.UIRegion;
import org.jdesktop.synthdesigner.synthmodel.UIState;

import java.awt.*;
import java.awt.geom.Point2D;
import java.beans.Beans;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.text.JTextComponent;

/**
 * PainterGenerator - Class for generating Painter class java source from a Canvas
 *
 * Following in the general theory that is used to generate a Painter file.
 *
 * Each Painter file represents a Region. So there is one painter file per region. In
 * skin.laf we support Icon subregions, which are really just hacked versions of the
 * parent region.
 *
 * In order to generate the most compact and efficient bytecode possible for the
 * Painters, we actually perform the generation sequence in two steps. The first
 * step is the analysis phase, where we walk through the SynthModel for the region
 * and discover commonality among the different states in the region. For example,
 * do they have common paths? Do they have common colors? Gradients? Is the painting
 * code for the different states identical other than for colors?
 *
 * We gather this information up. On the second pass, we use this data to determine the
 * methods that need to be generated, and the class variables that need to be generated.
 * We try to keep the actual bytecode count as small as possible so that we may reduce
 * the overall size of the look and feel significantly.
 *
 * @author  Richard Bair
 * @author  Jasper Potts
 */
public class PainterGenerator {
    private static String painterImplTemplate;
    private static String getPainterImplTemplate() {
        if (painterImplTemplate == null) {
            //load the painter template file into an in-memory string to improve performance
            //when generating a lot of classes
            try {
                painterImplTemplate = read("resources/PainterImpl.template");
            } catch (IOException e) {
                System.err.println("Failed to read template files.");
                throw new RuntimeException(e);
            }
        }
        return painterImplTemplate;
    }


    //a handful of counters, incremented whenever the associated object type is encounted.
    //These counters form the basis of the field and method suffixes.
    //These are all 1 based, because I felt like it :-)
    private int colorCounter = 1;
    private int gradientCounter = 1;
    private int radialCounter = 1;
    private int pathCounter = 1;
    private int rectCounter = 1;
    private int roundRectCounter = 1;
    private int ellipseCounter = 1;

    private int stateTypeCounter = 1;

    //during the first pass, we will construct these maps
    private Map<String, String> colors = new HashMap<String, String>();
    /**
     * Code=>method name.
     */
    private Map<String, String> methods = new HashMap<String, String>();

    //these variables hold the generated code
    /**
     * The source code in this variable will be used to define the various state types
     */
    private StringBuilder stateTypeCode = new StringBuilder();
    /**
     * The source code in this variable will be used to define the switch statement for painting
     */
    private StringBuilder switchCode = new StringBuilder();
    /**
     * The source code in this variable will be used to define the methods for painting each state
     */
    private StringBuilder paintingCode = new StringBuilder();
    /**
     * The source code in this variable will be used to add getExtendedCacheKeys
     * implementation if needed.
     */
    private StringBuilder getExtendedCacheKeysCode = new StringBuilder();
    /**
     * The source code in this variable will be used to define the methods for decoding gradients
     * and shapes.
     */
    private StringBuilder gradientsCode = new StringBuilder();
    private StringBuilder colorCode = new StringBuilder();
    private StringBuilder shapesCode = new StringBuilder();
    /**
     * Map of component colors keyed by state constant name
     */
    private Map<String, List<ComponentColor>> componentColorsMap =
            new LinkedHashMap<String, List<ComponentColor>>();
    /**
     * For the current state the list of all component colors used by this
     * painter, the index in this list is also the index in the runtime array
     * of defaults and keys.
     */
    private List<ComponentColor> componentColors = null;

    PainterGenerator(UIRegion r) {
        generate(r);
    }

    private void generate(UIRegion r) {
        for (UIState state : r.getBackgroundStates()) {
            System.out.println("------>" + state.getName());

            Canvas canvas = state.getCanvas();
            String type = r instanceof UIIconRegion ? ((UIIconRegion)r).getKey() : "Background";
            generate(state, canvas, type);
        }
        for (UIState state : r.getForegroundStates()) {
            Canvas canvas = state.getCanvas();
            generate(state, canvas, "Foreground");
        }
        for (UIState state : r.getBorderStates()) {
            Canvas canvas = state.getCanvas();
            generate(state, canvas, "Border");
        }
        //now check for any uiIconRegions, since these are collapsed together.
        for (UIRegion sub : r.getSubRegions()) {
            if (sub instanceof UIIconRegion) {
                generate(sub);
            }
        }
        //generate all the code for component colors
        if (!componentColorsMap.isEmpty()) {
            getExtendedCacheKeysCode
                    .append("    protected Object[] getExtendedCacheKeys(JComponent c) {\n")
                    .append("        Object[] extendedCacheKeys = null;\n")
                    .append("        switch(state) {\n");
            for (Map.Entry<String, List<ComponentColor>> entry : componentColorsMap.entrySet()) {
                getExtendedCacheKeysCode
                    .append("            case ")
                    .append(entry.getKey()).append(":\n")
                    .append("                extendedCacheKeys = new Object[] {\n");
                for (int i=0; i<entry.getValue().size(); i++) {
                    ComponentColor cc = entry.getValue().get(i);
                    getExtendedCacheKeysCode
                    .append("                     getComponentColor(c, \"")
                    .append(cc.propertyName).append("\", ")
                    .append(cc.defaultColorVariableName).append(", ")
                    .append(cc.saturationOffset).append("f, ")
                    .append(cc.brightnessOffset).append("f, ")
                    .append(cc.alphaOffset);
                    if (i + 1 < entry.getValue().size()) {
                        getExtendedCacheKeysCode.append("),\n");
                    } else {
                        getExtendedCacheKeysCode.append(")");
                    }
                }
                getExtendedCacheKeysCode.append("};\n")
                    .append("                break;\n");
            }
            getExtendedCacheKeysCode
                    .append("        }\n")
                    .append("        return extendedCacheKeys;\n")
                    .append("    }");
        }
    }

    //type is background, foreground, border, upArrowIcon, etc.
    private void generate(UIState state, Canvas canvas, String type) {
        String states = UIState.keysToString(state.getStateKeys());
        String stateType = toConstantName(type + "_" + states);
        String paintMethodName = "paint" + type + toClassName(states);
        //create new array for component colors for this state
        componentColors = new ArrayList<ComponentColor>();

        stateTypeCode.append("    static final int ").append(stateType).append(" = ").append(stateTypeCounter++).append(";\n");

        if (canvas.isBlank()) {
            return;
        }

        switchCode.append("            case ").append(stateType).append(": ").append(paintMethodName).append("(g); break;\n");
        paintingCode.append("    private void ").append(paintMethodName).append("(Graphics2D g) {\n");

        //start by setting up common info needed to encode the control points
        Insets in = canvas.getStretchingInsets();
        float a = in.left;
        float b = canvas.getSize().width - in.right;
        float c = in.top;
        float d = canvas.getSize().height - in.bottom;
        float width = canvas.getSize().width;
        float height = canvas.getSize().height;
        float cw = b - a;
        float ch = d - c;

        Layer[] layers = canvas.getLayers().toArray(new Layer[0]);
        for (int index=layers.length-1; index >= 0; index--) {
            Layer layer = layers[index];
            if (layer instanceof TemplateLayer) {
                continue;
            }
            //shapes must be painted in reverse order
            List<SimpleShape> shapes = layer.getShapes();
            for (int i=shapes.size()-1; i>=0; i--) {
                SimpleShape s = shapes.get(i);
                if (s instanceof PaintedShape) {
                    PaintedShape shape = (PaintedShape)s;
                    PaintModel paint = shape.getPaintModel();

                    /*
                        We attempt to write the minimal number of bytecodes as possible when
                        generating code. Due to the inherit complexities in determining what
                        is extraneous, we use the following system:

                        We first generate the code for the shape. Then, we check to see if
                        this shape has already been generated. If so, then we defer to an
                        existing method. If not, then we will create a new methods, stick
                        the code in it, and refer to that method.
                    */

                    String shapeMethodName = null; // will contain the name of the method which creates the shape
                    String shapeVariable = null; // will be one of rect, roundRect, ellipse, or path.
                    String shapeMethodBody = null;

                    if (shape instanceof RectangleShape) {
                        RectangleShape rshape = (RectangleShape) shape;
                        float x1 = encode((float)rshape.getX1(), a, b, width);
                        float y1 = encode((float)rshape.getY1(), c, d, height);
                        float x2 = encode((float)rshape.getX2(), a, b, width);
                        float y2 = encode((float)rshape.getY2(), c, d, height);
                        if (rshape.isRounded()) {
                            //it is a rounded rectangle
                            float rounding = (float)rshape.getRounding();

                            shapeMethodBody =
                                    "        roundRect.setRoundRect(" +
                                    writeDecodeX(x1) + ", //x\n" +
                                    "                               " + writeDecodeY(y1) + ", //y\n" +
                                    "                               " + writeDecodeX(x2) + " - " + writeDecodeX(x1) + ", //width\n" +
                                    "                               " + writeDecodeY(y2) + " - " + writeDecodeY(y1) + ", //height\n" +
                                    "                               " + rounding + "f, " + rounding + "f); //rounding";
                            shapeVariable = "roundRect";
                        } else {
                            shapeMethodBody =
                                    "            rect.setRect(" +
                                    writeDecodeX(x1) + ", //x\n" +
                                    "                         " + writeDecodeY(y1) + ", //y\n" +
                                    "                         " + writeDecodeX(x2) + " - " + writeDecodeX(x1) + ", //width\n" +
                                    "                         " + writeDecodeY(y2) + " - " + writeDecodeY(y1) + "); //height";
                            shapeVariable = "rect";
                        }
                    } else if (shape instanceof EllipseShape) {
                        EllipseShape eshape = (EllipseShape) shape;
                        float x1 = encode((float)eshape.getX1(), a, b, width);
                        float y1 = encode((float)eshape.getY1(), c, d, height);
                        float x2 = encode((float)eshape.getX2(), a, b, width);
                        float y2 = encode((float)eshape.getY2(), c, d, height);
                        shapeMethodBody =
                                "        ellipse.setFrame(" +
                                writeDecodeX(x1) + ", //x\n" +
                                "                         " + writeDecodeY(y1) + ", //y\n" +
                                "                         " + writeDecodeX(x2) + " - " + writeDecodeX(x1) + ", //width\n" +
                                "                         " + writeDecodeY(y2) + " - " + writeDecodeY(y1) + "); //height";
                        shapeVariable = "ellipse";
                    } else if (shape instanceof PathShape) {
                        PathShape pshape = (PathShape) shape;
                        List<BezierControlPoint> controlPoints = pshape.getBezierControlPoints();
                        BezierControlPoint first, last;
                        first = last = controlPoints.get(0);
                        StringBuilder buffer = new StringBuilder();
                        buffer.append("        path.reset();\n");
                        buffer.append("        path.moveTo(" + writeDecodeX(encode((float)first.getX(), a, b, width)) + ", " + writeDecodeY(encode((float)first.getY(), c, d, height)) + ");\n");
                        for (int j=1; j<controlPoints.size(); j++) {
                            BezierControlPoint cp = controlPoints.get(j);
                            if (last.getCp2().isSharp() && cp.getCp1().isSharp()) {
                                float x = encode((float)cp.getX(), a, b, width);
                                float y = encode((float)cp.getY(), c, d, height);
                                buffer.append("        path.lineTo(" + writeDecodeX(x) + ", " + writeDecodeY(y) + ");\n");
                            } else {
                                float x1 = encode((float)last.getX(), a, b, width);
                                float y1 = encode((float)last.getY(), c, d, height);
                                float x2 = encode((float)cp.getX(), a, b, width);
                                float y2 = encode((float)cp.getY(), c, d, height);
                                buffer.append(
                                        "        path.curveTo(" + writeDecodeBezierX(x1, last.getX(), last.getCp2X()) + ", "
                                                                + writeDecodeBezierY(y1, last.getY(), last.getCp2Y()) + ", "
                                                                + writeDecodeBezierX(x2, cp.getX(), cp.getCp1X()) + ", "
                                                                + writeDecodeBezierY(y2, cp.getY(), cp.getCp1Y()) + ", "
                                                                + writeDecodeX(x2) + ", " + writeDecodeY(y2) + ");\n");
                            }
                            last = cp;
                        }
                        if (last.getCp2().isSharp() && first.getCp1().isSharp()) {
                            float x = encode((float)first.getX(), a, b, width);
                            float y = encode((float)first.getY(), c, d, height);
                            buffer.append("        path.lineTo(" + writeDecodeX(x) + ", " + writeDecodeY(y) + ");\n");
                        } else {
                            float x1 = encode((float)last.getX(), a, b, width);
                            float y1 = encode((float)last.getY(), c, d, height);
                            float x2 = encode((float)first.getX(), a, b, width);
                            float y2 = encode((float)first.getY(), c, d, height);
                            buffer.append(
                                    "        path.curveTo(" + writeDecodeBezierX(x1, last.getX(), last.getCp2X()) + ", "
                                                            + writeDecodeBezierY(y1, last.getY(), last.getCp2Y()) + ", "
                                                            + writeDecodeBezierX(x2, first.getX(), first.getCp1X()) + ", "
                                                            + writeDecodeBezierY(y2, first.getY(), first.getCp1Y()) + ", "
                                                            + writeDecodeX(x2) + ", " + writeDecodeY(y2) + ");\n");
                        }
                        buffer.append("        path.closePath();");
                        shapeMethodBody = buffer.toString();
                        shapeVariable = "path";
                    } else {
                        throw new RuntimeException("Cannot happen unless a new Shape has been defined");
                    }

                    //now that we have the shape defined in shapeMethodBody, and a shapeVariable name,
                    //look to see if such a body has been previously defined.
                    shapeMethodName = methods.get(shapeMethodBody);
                    String returnType = null;
                    if (shapeMethodName == null) {
                        if ("rect".equals(shapeVariable)) {
                            shapeMethodName = "decodeRect" + rectCounter++;
                            returnType = "Rectangle2D";
                        } else if ("roundRect".equals(shapeVariable)) {
                            shapeMethodName = "decodeRoundRect" + roundRectCounter++;
                            returnType = "RoundRectangle2D";
                        } else if ("ellipse".equals(shapeVariable)) {
                            shapeMethodName = "decodeEllipse" + ellipseCounter++;
                            returnType = "Ellipse2D";
                        } else {
                            shapeMethodName = "decodePath" + pathCounter++;
                            returnType = "Path2D";
                        }
                        methods.put(shapeMethodBody, shapeMethodName);

                        //since the method wasn't previously defined, time to define it
                        shapesCode.append("    private ").append(returnType).append(" ").append(shapeMethodName).append("() {\n");
                        shapesCode.append(shapeMethodBody);
                        shapesCode.append("\n");
                        shapesCode.append("        return " + shapeVariable + ";\n");
                        shapesCode.append("    }\n\n");
                    }

                    //now that the method has been defined, I can go on and decode the
                    //paint. After the paint is decoded, I can write the g.fill() method call,
                    //using the result of the shapeMethodName. Yay!

//            if (shapeVariable != null) {
                //first, calculate the bounds of the shape being painted and store in variables
                    paintingCode.append("        ").append(shapeVariable).append(" = ").append(shapeMethodName).append("();\n");

                    if (paint instanceof Matte) {
                        String colorVariable = encodeMatte((Matte)paint);
                        paintingCode.append("        g.setPaint(").append(colorVariable).append(");\n");
                    } else if (paint instanceof Gradient) {
                        String gradientMethodName = encodeGradient(shape, (Gradient)paint);
                        paintingCode.append("        g.setPaint(").append(gradientMethodName).append("(").append(shapeVariable).append("));\n");
                    } else if (paint instanceof RadialGradient) {
                        String radialMethodName = encodeRadial(shape, (RadialGradient)paint);
                        paintingCode.append("        g.setPaint(").append(radialMethodName).append("(").append(shapeVariable).append("));\n");
                    }
                    paintingCode.append("        g.fill(").append(shapeVariable).append(");\n");
                }
            }
        }

        paintingCode.append("\n    }\n\n");

        //collect component colors
        if (!componentColors.isEmpty()) {
            componentColorsMap.put(stateType, componentColors);
            componentColors = null;
        }
    }

    private float encode(float x, float a, float b, float width) {
        return ObjectCodeConvertors.encode(x, a, b, width);
    }

    private String writeDecodeX(float encodedX) {
        return "decodeX(" + encodedX + "f)";
    }

    private String writeDecodeY(float encodedY) {
        return "decodeY(" + encodedY + "f)";
    }

    /**
     *
     * @param ex encoded x value
     * @param x unencoded x value
     * @param cpx unencoded cpx value
     * @return
     */
    private static String writeDecodeBezierX(double ex, double x, double cpx) {
        return "decodeAnchorX(" + ex + "f, " + (cpx - x) + "f)";
    }

    /**
     *
     * @param ey encoded y value
     * @param y unencoded y value
     * @param cpy unencoded cpy value
     * @return
     */
    private static String writeDecodeBezierY(double ey, double y, double cpy) {
        return "decodeAnchorY(" + ey + "f, " + (cpy - y) + "f)";
    }

    private String encodeMatte(Matte m) {
        String declaration = null;
        if (m.isAbsolute()) {
            Color c = m.getColor();
            declaration = ObjectCodeConvertors.convert(c);
        } else {
            declaration = "decodeColor(\"" + m.getUiDefaultParentName() +
                          "\", " + m.getHueOffset() + "f, " +
                          m.getSaturationOffset() + "f, " +
                          m.getBrightnessOffset() + "f, " +
                          m.getAlphaOffset() + ")";
        }

        String variableName = colors.get(declaration);
        if (variableName == null) {
            variableName = "color" + colorCounter++;
            colors.put(declaration, variableName);
            colorCode.append("    private Color ").append(variableName).append(" = ");
            colorCode.append(declaration).append(";\n");
        }
        // handle component colors
        if (m.getComponentPropertyName() != null) {
            ComponentColor cc = new ComponentColor(m.getComponentPropertyName(),
                    variableName, m.getSaturationOffset(),
                    m.getBrightnessOffset(), m.getAlphaOffset());
            int index = componentColors.indexOf(cc);
            if (index == -1) {
                index = componentColors.size();
                componentColors.add(cc);
            }
            return "(Color)componentColors[" + index + "]";
        } else {
            return variableName;
        }
    }

    private String encodeColor(Color c) {
        String declaration = ObjectCodeConvertors.convert(c);
        String variableName = colors.get(declaration);
        if (variableName == null) {
            variableName = "color" + colorCounter++;
            colors.put(declaration, variableName);
            colorCode.append("    private Color ").append(variableName).append(" = ");
            colorCode.append(declaration).append(";\n");
        }

        return variableName;
    }

    private String encodeGradient(PaintedShape ps, Gradient g) {
        StringBuilder b = new StringBuilder();
        float x1 = (float)ps.getPaintX1();
        float y1 = (float)ps.getPaintY1();
        float x2 = (float)ps.getPaintX2();
        float y2 = (float)ps.getPaintY2();
        b.append("        return decodeGradient((");
        b.append(x1);
        b.append("f * w) + x, (");
        b.append(y1);
        b.append("f * h) + y, (");
        b.append(x2);
        b.append("f * w) + x, (");
        b.append(y2);
        b.append("f * h) + y,\n");
        encodeGradientColorsAndFractions(g,b);
        b.append(");");

        String methodBody = b.toString();
        String methodName = methods.get(methodBody);
        if (methodName == null) {
            methodName = "decodeGradient" + gradientCounter++;
            gradientsCode.append("    private Paint ").append(methodName).append("(Shape s) {\n");
            gradientsCode.append("        Rectangle2D bounds = s.getBounds2D();\n");
            gradientsCode.append("        float x = (float)bounds.getX();\n");
            gradientsCode.append("        float y = (float)bounds.getY();\n");
            gradientsCode.append("        float w = (float)bounds.getWidth();\n");
            gradientsCode.append("        float h = (float)bounds.getHeight();\n");
            gradientsCode.append(methodBody);
            gradientsCode.append("\n    }\n\n");
            methods.put(methodBody, methodName);
        }
        return methodName;
    }

    /**
     * Takes a abstract gradient and creates the code for the fractions float
     * array and the colors array that can be used in the constructors of linear
     * and radial gradients.
     *
     * @param g The abstract gradient to get stops from
     * @param b Append code string of the form "new float[]{...},
     *          new Color[]{...}" to this StringBuilder
     */
    private void encodeGradientColorsAndFractions(AbstractGradient g,
                                                    StringBuilder b) {
        List<GradientStop> stops = g.getStops();
        // there are stops.size() number of main stops. Between each is a
        // fractional stop. Thus, there are: stops.size() + stops.size() - 1
        // number of fractions and colors.
        float[] fractions = new float[stops.size() + stops.size() - 1];
        String[] colors = new String[fractions.length];
        //for each stop, create the stop and it's associated fraction
        int index = 0; // the index into fractions and colors
        for (int i = 0; i < stops.size(); i++) {
            GradientStop s = stops.get(i);
            //copy over the stop's data
            colors[index] = encodeMatte(s.getColor());
            fractions[index] = s.getPosition();

            //If this isn't the last stop, then add in the fraction
            if (index < fractions.length - 1) {
                float f1 = s.getPosition();
                float f2 = stops.get(i + 1).getPosition();
                index++;
                fractions[index] = f1 + (f2 - f1) * s.getMidpoint();
                colors[index] = "decodeColor("+
                        colors[index - 1]+","+
                        encodeMatte(stops.get(i + 1).getColor())+",0.5f)";
            }
            index++;
        }
        // Check boundry conditions
        for (int i = 1; i < fractions.length; i++) {
            //to avoid an error with LinearGradientPaint where two fractions
            //are identical, bump up the fraction value by a miniscule amount
            //if it is identical to the previous one
            //NOTE: The <= is critical because the previous value may already
            //have been bumped up
            if (fractions[i] <= fractions[i - 1]) {
                fractions[i] = fractions[i - 1] + .000001f;
            }
        }
        //another boundary condition where multiple stops are all at the end. The
        //previous loop bumped all but one of these past 1.0, which is bad.
        //so remove any fractions (and their colors!) that are beyond 1.0
        int outOfBoundsIndex = -1;
        for (int i = 0; i < fractions.length; i++) {
            if (fractions[i] > 1) {
                outOfBoundsIndex = i;
                break;
            }
        }
        if (outOfBoundsIndex >= 0) {
            float[] f = fractions;
            String[] c = colors;
            fractions = new float[outOfBoundsIndex];
            colors = new String[outOfBoundsIndex];
            System.arraycopy(f, 0, fractions, 0, outOfBoundsIndex);
            System.arraycopy(c, 0, colors, 0, outOfBoundsIndex);
        }
        // build string
        b.append("                new float[] { ");
        for (int i = 0; i < fractions.length; i++) {
            if (i>0)b.append(',');
            b.append(fractions[i]);
            b.append('f');
        }
        b.append(" },\n                new Color[] { ");
        for (int i = 0; i < colors.length; i++) {
            if (i>0) b.append(",\n                            ");
            b.append(colors[i]);
        }
        b.append("}");
    }

    private String encodeRadial(PaintedShape ps, RadialGradient g) {
        float centerX1 = (float)ps.getPaintX1();
        float centerY1 = (float)ps.getPaintY1();
        float x2 = (float)ps.getPaintX2();
        float y2 = (float)ps.getPaintY2();
        float radius = (float)Point2D.distance(centerX1, centerY1, x2, y2);
        StringBuilder b = new StringBuilder();

        b.append("        return decodeRadialGradient((");
        b.append(centerX1);
        b.append("f * w) + x, (");
        b.append(centerY1);
        b.append("f * h) + y, ");
        b.append(radius);
        b.append("f,\n");
        encodeGradientColorsAndFractions(g,b);
        b.append(");");

        String methodBody = b.toString();
        String methodName = methods.get(methodBody);
        if (methodName == null) {
            methodName = "decodeRadial" + radialCounter++;
            gradientsCode.append("    private Paint ").append(methodName).append("(Shape s) {\n");
            gradientsCode.append("        Rectangle2D bounds = s.getBounds2D();\n");
            gradientsCode.append("        float x = (float)bounds.getX();\n");
            gradientsCode.append("        float y = (float)bounds.getY();\n");
            gradientsCode.append("        float w = (float)bounds.getWidth();\n");
            gradientsCode.append("        float h = (float)bounds.getHeight();\n");
            gradientsCode.append(methodBody);
            gradientsCode.append("\n    }\n\n");
            methods.put(methodBody, methodName);
        }
        return methodName;
    }

    //note that this method is not thread-safe. In fact, none of this class is.
    static void writePainter(UIRegion r, Map<String, String> variables, File painterPackageRoot, String prefix) throws IOException {
        //Need only write out the stuff for this region, don't need to worry about subregions
        //since this method will be called for each of those (and they go in their own file, anyway).
        //The only subregion that we compound into this is the one for icons.

        String painterName = makePretty(prefix) + "Painter";
        PainterGenerator gen = new PainterGenerator(r);
        System.out.println("Generating source file: " + painterName + ".java");
        System.out.println(gen.stateTypeCode.toString());
        variables.put("PAINTER_NAME", painterName);
        variables.put("STATIC_DECL", gen.stateTypeCode.toString());
        variables.put("COLORS_DECL", gen.colorCode.toString());
        variables.put("DO_PAINT_SWITCH_BODY", gen.switchCode.toString());
        variables.put("PAINTING_DECL", gen.paintingCode.toString());
        variables.put("GET_EXTENDED_CACHE_KEYS", gen.getExtendedCacheKeysCode.toString());
        variables.put("SHAPES_DECL", gen.shapesCode.toString());
        variables.put("GRADIENTS_DECL", gen.gradientsCode.toString());

        writeSrcFile(getPainterImplTemplate(), variables, new File(painterPackageRoot, painterName + ".java"));

        variables.remove("PAINTER_NAME");
        variables.remove("STATIC_DECL");
        variables.remove("COLORS_DECL");
        variables.remove("DO_PAINT_SWITCH_BODY");
        variables.remove("PAINTING_DECL");
        variables.remove("SHAPES_DECL");
        variables.remove("GRADIENTS_DECL");
    }

    /**
     * Represents the usage of a color from a component within a painter. That
     * is, a painter can use colors from the component when it paints. This
     * class represents the usage of such a color, containing a reference to
     * the name of the property that contains the color, and the various
     * derivation offsets to apply to that color.
     */
    private static class ComponentColor {
        public String propertyName;
        public String defaultColorVariableName;
        private float saturationOffset = 0, brightnessOffset = 0;
        private int alphaOffset = 0;

        private ComponentColor(String propertyName,
                               String defaultColorVariableName,
                               float saturationOffset,
                               float brightnessOffset,
                               int alphaOffset) {
            this.propertyName = propertyName;
            this.defaultColorVariableName = defaultColorVariableName;
            this.saturationOffset = saturationOffset;
            this.brightnessOffset = brightnessOffset;
            this.alphaOffset = alphaOffset;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ComponentColor c = (ComponentColor)o;
            if (alphaOffset != c.alphaOffset) return false;
            if (Float.compare(saturationOffset, c.saturationOffset) != 0)
                return false;
            if (Float.compare(brightnessOffset, c.brightnessOffset) != 0)
                return false;
            if (defaultColorVariableName != null ?
                    !defaultColorVariableName.equals(c.defaultColorVariableName) :
                    c.defaultColorVariableName != null) return false;
            if (propertyName != null ? !propertyName.equals(c.propertyName) :
                    c.propertyName != null) return false;
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 61 * hash + (this.propertyName != null ?
                this.propertyName.hashCode() : 0);
            hash = 61 * hash + (this.defaultColorVariableName != null ?
                this.defaultColorVariableName.hashCode() : 0);
            hash = 61 * hash + Float.floatToIntBits(this.saturationOffset);
            hash = 61 * hash + Float.floatToIntBits(this.brightnessOffset);
            hash = 61 * hash + this.alphaOffset;
            return hash;
        }
    }
}
