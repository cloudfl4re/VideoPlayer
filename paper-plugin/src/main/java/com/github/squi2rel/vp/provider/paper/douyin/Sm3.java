package com.github.squi2rel.vp.provider.paper.douyin;

import java.util.Arrays;

final class Sm3 {
    private static final int[] INITIAL = {
            0x7380166f, 0x4914b2b9, 0x172442d7, 0xda8a0600,
            0xa96f30bc, 0x163138aa, 0xe38dee4d, 0xb0fb0e4e
    };

    private Sm3() {
    }

    static byte[] digest(byte[] input) {
        byte[] padded = pad(input == null ? new byte[0] : input);
        int[] state = INITIAL.clone();
        int[] words = new int[68];
        int[] expanded = new int[64];
        for (int offset = 0; offset < padded.length; offset += 64) {
            for (int i = 0; i < 16; i++) words[i] = readInt(padded, offset + i * 4);
            for (int i = 16; i < 68; i++) {
                int mixed = words[i - 16] ^ words[i - 9] ^ Integer.rotateLeft(words[i - 3], 15);
                words[i] = p1(mixed) ^ Integer.rotateLeft(words[i - 13], 7) ^ words[i - 6];
            }
            for (int i = 0; i < 64; i++) expanded[i] = words[i] ^ words[i + 4];
            compress(state, words, expanded);
        }
        byte[] output = new byte[32];
        for (int i = 0; i < state.length; i++) writeInt(output, i * 4, state[i]);
        Arrays.fill(words, 0);
        Arrays.fill(expanded, 0);
        return output;
    }

    private static byte[] pad(byte[] input) {
        long bitLength = (long) input.length * 8L;
        int zeroes = (56 - (input.length + 1) % 64 + 64) % 64;
        byte[] padded = Arrays.copyOf(input, input.length + 1 + zeroes + 8);
        padded[input.length] = (byte) 0x80;
        for (int i = 0; i < 8; i++) padded[padded.length - 1 - i] = (byte) (bitLength >>> (i * 8));
        return padded;
    }

    private static void compress(int[] state, int[] words, int[] expanded) {
        int a = state[0];
        int b = state[1];
        int c = state[2];
        int d = state[3];
        int e = state[4];
        int f = state[5];
        int g = state[6];
        int h = state[7];
        for (int round = 0; round < 64; round++) {
            int constant = round < 16 ? 0x79cc4519 : 0x7a879d8a;
            int rotatedA = Integer.rotateLeft(a, 12);
            int ss1 = Integer.rotateLeft(rotatedA + e + Integer.rotateLeft(constant, round), 7);
            int ss2 = ss1 ^ rotatedA;
            int tt1 = ff(a, b, c, round) + d + ss2 + expanded[round];
            int tt2 = gg(e, f, g, round) + h + ss1 + words[round];
            d = c;
            c = Integer.rotateLeft(b, 9);
            b = a;
            a = tt1;
            h = g;
            g = Integer.rotateLeft(f, 19);
            f = e;
            e = p0(tt2);
        }
        state[0] ^= a;
        state[1] ^= b;
        state[2] ^= c;
        state[3] ^= d;
        state[4] ^= e;
        state[5] ^= f;
        state[6] ^= g;
        state[7] ^= h;
    }

    private static int ff(int x, int y, int z, int round) {
        return round < 16 ? x ^ y ^ z : (x & y) | (x & z) | (y & z);
    }

    private static int gg(int x, int y, int z, int round) {
        return round < 16 ? x ^ y ^ z : (x & y) | (~x & z);
    }

    private static int p0(int value) {
        return value ^ Integer.rotateLeft(value, 9) ^ Integer.rotateLeft(value, 17);
    }

    private static int p1(int value) {
        return value ^ Integer.rotateLeft(value, 15) ^ Integer.rotateLeft(value, 23);
    }

    private static int readInt(byte[] input, int offset) {
        return (input[offset] & 0xff) << 24
                | (input[offset + 1] & 0xff) << 16
                | (input[offset + 2] & 0xff) << 8
                | input[offset + 3] & 0xff;
    }

    private static void writeInt(byte[] output, int offset, int value) {
        output[offset] = (byte) (value >>> 24);
        output[offset + 1] = (byte) (value >>> 16);
        output[offset + 2] = (byte) (value >>> 8);
        output[offset + 3] = (byte) value;
    }
}
