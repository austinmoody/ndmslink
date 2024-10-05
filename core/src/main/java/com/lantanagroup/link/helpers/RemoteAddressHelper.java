package com.lantanagroup.link.helpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

public class RemoteAddressHelper {
    private static final Logger logger = LoggerFactory.getLogger(RemoteAddressHelper.class);
    private static final List<String> IP_HEADERS = Arrays.asList(
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
    );

    private RemoteAddressHelper() {
        throw new IllegalStateException("Helper class");
    }

    public static String getRemoteAddress(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (isValidIp(ip)) {
                logger.debug("IP found in header {}: {}", header, ip);
                return ip;
            }
        }

        String remoteAddr = request.getRemoteAddr();
        logger.debug("No valid IP found in headers. Use remote address: {}", remoteAddr);
        return remoteAddr;
    }

    private static boolean isValidIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            return false;
        }

        // if it is a list just take the first
        if (ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }

        // regex it
        return ipAddress.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    }
}
