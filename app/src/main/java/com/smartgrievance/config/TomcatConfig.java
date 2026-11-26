package com.smartgrievance.config;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * Tomcat rejects an encoded slash (%2F) in the path itself and answers with its own unstyled 400 page. Passing it
 * through lets Spring Security's StrictHttpFirewall reject it instead, so the request gets the application's 400 page
 * like every other firewall rejection (doc 12 D85). The path is still refused; nothing is decoded.
 */
@Component
class TomcatConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(connector ->
                connector.setEncodedSolidusHandling(EncodedSolidusHandling.PASS_THROUGH.getValue()));
    }
}
