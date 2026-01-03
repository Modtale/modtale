package net.modtale.service.security;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Service;

@Service
public class SanitizationService {

    private final PolicyFactory policy = new HtmlPolicyBuilder()
            .allowElements("p", "div", "span", "br", "hr", "h1", "h2", "h3", "h4", "h5", "h6",
                    "strong", "b", "em", "i", "u", "strike", "ul", "ol", "li", "blockquote",
                    "table", "thead", "tbody", "tr", "td", "th", "a", "img")
            .allowAttributes("href", "target", "rel").onElements("a")
            .allowAttributes("src", "alt", "title", "width", "height").onElements("img")
            .allowAttributes("align").onElements("p", "div", "img")
            .allowStandardUrlProtocols()
            .requireRelNofollowOnLinks()
            .toFactory();

    public String sanitize(String input) {
        if (input == null) return null;
        return policy.sanitize(input);
    }

    public String sanitizePlainText(String input) {
        if (input == null) return null;
        return input.replaceAll("<[^>]*>", "").trim();
    }
}