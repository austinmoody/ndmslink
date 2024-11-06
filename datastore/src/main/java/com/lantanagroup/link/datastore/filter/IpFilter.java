package com.lantanagroup.link.datastore.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public class IpFilter implements Filter {
    private final List<String> allowedIps;
    private final boolean filterEnabled;
    private static final Logger logger = LoggerFactory.getLogger(IpFilter.class);

    public IpFilter(boolean enabled, List<String> allowedIps) {
        this.filterEnabled = enabled;
        this.allowedIps = allowedIps;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        if (!filterEnabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        boolean isAllowed = false;

        for (String ip : allowedIps) {
            IpAddressMatcher matcher = new IpAddressMatcher(ip);
            if (matcher.matches(httpRequest)) {
                isAllowed = true;
                break;
            }
        }

        if (!isAllowed) {
            logger.info("IP Address {} Not Allowed", httpRequest.getRemoteAddr());
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
            return;
        }

        chain.doFilter(request, response);
    }
}
