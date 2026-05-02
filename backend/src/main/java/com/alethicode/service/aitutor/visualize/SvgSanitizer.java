package com.alethicode.service.aitutor.visualize;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SvgSanitizer {

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "svg", "g", "rect", "circle", "ellipse", "line", "path", "polyline", "polygon",
            "text", "tspan", "defs", "marker", "linearGradient", "stop", "title", "desc"
    );

    private static final Set<String> ALLOWED_ATTRS = Set.of(
            "x", "y", "width", "height", "cx", "cy", "r", "rx", "ry", "d", "points",
            "x1", "x2", "y1", "y2", "dx", "dy",
            "stroke", "stroke-width", "stroke-dasharray", "stroke-linecap", "stroke-linejoin",
            "fill", "fill-opacity",
            "font-size", "font-family", "font-weight", "text-anchor", "dominant-baseline",
            "transform", "marker-end", "marker-start",
            "viewBox", "xmlns", "id", "class",
            "offset", "stop-color", "stop-opacity", "gradientUnits"
    );

    private static final int MAX_WIDTH = 800;
    private static final int MAX_HEIGHT = 600;

    public String sanitize(String rawSvg) {
        if (rawSvg == null || rawSvg.isBlank()) {
            throw new VisualizeValidationException("svg payload is empty");
        }
        Document doc = Jsoup.parse(rawSvg, "", Parser.xmlParser());
        Elements roots = doc.getElementsByTag("svg");
        if (roots.isEmpty()) {
            throw new VisualizeValidationException("svg root <svg> missing");
        }
        Element root = roots.first();

        Elements forbiddenChildren = root.select("script, foreignObject, animate, animateTransform, " +
                "animateMotion, set, iframe, image, use, embed, object, a");
        if (!forbiddenChildren.isEmpty()) {
            throw new VisualizeValidationException(
                    "svg contains forbidden element: " + forbiddenChildren.first().tagName());
        }

        stripDisallowed(root);
        enforceSize(root);
        return root.outerHtml();
    }

    private void stripDisallowed(Element node) {
        for (Element child : new java.util.ArrayList<>(node.children())) {
            stripDisallowed(child);
        }
        if (!ALLOWED_TAGS.contains(node.tagName())) {
            node.remove();
            return;
        }
        for (org.jsoup.nodes.Attribute attr : node.attributes().asList()) {
            String key = attr.getKey();
            String value = attr.getValue() == null ? "" : attr.getValue();
            String lowerVal = value.toLowerCase();
            if (key.startsWith("on")
                    || lowerVal.contains("javascript:")
                    || lowerVal.contains("<script")
                    || key.equalsIgnoreCase("href")
                    || key.equalsIgnoreCase("xlink:href")
                    || key.equalsIgnoreCase("style")
                    || key.equalsIgnoreCase("formaction")) {
                node.removeAttr(key);
                continue;
            }
            if (!ALLOWED_ATTRS.contains(key)) {
                node.removeAttr(key);
            }
        }
    }

    private void enforceSize(Element root) {
        String widthRaw = root.attr("width");
        String heightRaw = root.attr("height");
        try {
            if (!widthRaw.isEmpty()) {
                int w = Integer.parseInt(widthRaw.replaceAll("[^0-9]", ""));
                if (w > MAX_WIDTH) {
                    throw new VisualizeValidationException("svg width " + w + " exceeds " + MAX_WIDTH);
                }
            }
            if (!heightRaw.isEmpty()) {
                int h = Integer.parseInt(heightRaw.replaceAll("[^0-9]", ""));
                if (h > MAX_HEIGHT) {
                    throw new VisualizeValidationException("svg height " + h + " exceeds " + MAX_HEIGHT);
                }
            }
        } catch (NumberFormatException ignored) {
        }
    }
}
