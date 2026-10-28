package com.zwf.aisupport.service.impl;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

final class ExternalUrlPolicy {

    URI requirePublicHttpUrl(String rawUrl) throws IOException {
        final URI uri;
        try {
            uri = URI.create(rawUrl).normalize();
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid external URL", exception);
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || host == null || uri.getUserInfo() != null || !isAllowedPort(uri.getPort())) {
            throw new IOException("External URL is not permitted");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.endsWith(".")) {
            normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        }
        if (isLocalHostname(normalizedHost)) {
            throw new IOException("Local and internal hosts are not permitted");
        }

        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(normalizedHost);
        } catch (UnknownHostException exception) {
            throw new IOException("External host could not be resolved", exception);
        }
        if (addresses.length == 0) {
            throw new IOException("External host has no addresses");
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new IOException("External host resolves to a non-public address");
            }
        }
        return uri;
    }

    private boolean isAllowedPort(int port) {
        return port == -1 || port == 80 || port == 443;
    }

    private boolean isLocalHostname(String host) {
        return host.equals("localhost") || host.endsWith(".localhost")
                || host.endsWith(".local") || host.endsWith(".internal")
                || host.endsWith(".home.arpa") || host.equals("metadata.google.internal")
                || host.equals("metadata.google");
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first != 0 && first < 224
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && (second == 0 || second == 88 && third == 99
                    || second == 168 || second == 0 && third == 2))
                    && !(first == 198 && (second == 18 || second == 19 || second == 51 && third == 100))
                    && !(first == 203 && second == 0 && third == 113);
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            boolean globalUnicast = (first & 0xe0) == 0x20;
            boolean specialPurpose = first == 0x20 && second == 0x01 && (third & 0xfe) == 0
                    || first == 0x20 && second == 0x01 && third == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8
                    || first == 0x20 && second == 0x02;
            return globalUnicast && !specialPurpose;
        }
        return false;
    }
}
