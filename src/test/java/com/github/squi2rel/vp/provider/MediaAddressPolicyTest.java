package com.github.squi2rel.vp.provider;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaAddressPolicyTest {
    @Test
    void blocksIpv4MappedLoopbackAddresses() throws Exception {
        InetAddress mapped = InetAddress.getByName("::ffff:127.0.0.1");

        assertTrue(MediaAddressPolicy.isBlocked(mapped));
        assertFalse(MediaAddressPolicy.isAllowed("http://mapped.example", ignored -> new InetAddress[]{mapped}));
    }

    @Test
    void blocksPrivateAndMetadataRanges() throws Exception {
        assertTrue(MediaAddressPolicy.isBlocked(InetAddress.getByName("10.0.0.1")));
        assertTrue(MediaAddressPolicy.isBlocked(InetAddress.getByName("192.168.1.1")));
        assertTrue(MediaAddressPolicy.isBlocked(InetAddress.getByName("169.254.169.254")));
        assertTrue(MediaAddressPolicy.isBlocked(InetAddress.getByName("192.0.0.8")));
        assertFalse(MediaAddressPolicy.isAllowed("http://internal.example", ignored -> new InetAddress[]{
                InetAddress.getByName("10.1.2.3")
        }));
    }
}
