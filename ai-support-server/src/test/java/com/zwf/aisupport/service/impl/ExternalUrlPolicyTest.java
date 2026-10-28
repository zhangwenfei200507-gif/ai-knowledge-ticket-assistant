package com.zwf.aisupport.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalUrlPolicyTest {

    private final ExternalUrlPolicy policy = new ExternalUrlPolicy();

    @Test
    void rejectsNonHttpSchemesAndCredentials() {
        assertRejected("file:///etc/passwd");
        assertRejected("https://user:password@example.com/article");
    }

    @Test
    void rejectsLocalAndMetadataHosts() {
        assertRejected("http://localhost/admin");
        assertRejected("http://metadata.google.internal/computeMetadata/v1/");
        assertRejected("http://169.254.169.254/latest/meta-data/");
    }

    @Test
    void rejectsPrivateAddressesAndUnexpectedPorts() {
        assertRejected("http://10.0.0.4/article");
        assertRejected("http://192.168.1.20:8080/article");
        assertRejected("https://example.com:8443/article");
    }

    @Test
    void rejectsNonGlobalIpv6Ranges() {
        assertRejected("http://[::1]/admin");
        assertRejected("http://[fc00::1]/private");
        assertRejected("https://[2001:db8::1]/documentation");
        assertRejected("https://[2002:c000:0201::1]/legacy-tunnel");
    }

    private void assertRejected(String url) {
        assertThatThrownBy(() -> policy.requirePublicHttpUrl(url))
                .isInstanceOf(IOException.class);
    }
}
