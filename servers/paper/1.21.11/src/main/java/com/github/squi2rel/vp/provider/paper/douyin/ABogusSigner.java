package com.github.squi2rel.vp.provider.paper.douyin;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.function.LongSupplier;

final class ABogusSigner {
    private static final String CHARSET = "Dkdpgh2ZmsQB80/MfvV36XI1R45-WUAlEixNLwoqYTOPuzKFjJnry79HbGcaStCe";
    private static final byte[] BROWSER = "1536|742|1536|864|0|0|0|0|1536|864|1536|864|1536|742|24|24|MacIntel"
            .getBytes(StandardCharsets.US_ASCII);
    private static final int[] UA_CODE = {
            76, 98, 15, 131, 97, 245, 224, 133, 122, 199,
            241, 166, 79, 34, 90, 191, 128, 126, 122, 98,
            66, 11, 14, 40, 49, 110, 110, 173, 67, 96, 138, 252
    };
    private final SecureRandom random;
    private final LongSupplier currentTimeMillis;

    ABogusSigner() {
        this(new SecureRandom(), System::currentTimeMillis);
    }

    ABogusSigner(SecureRandom random, LongSupplier currentTimeMillis) {
        this.random = random;
        this.currentTimeMillis = currentTimeMillis;
    }

    String sign(String query) {
        long start = currentTimeMillis.getAsLong();
        long end = start + 4L + random.nextInt(5);
        return sign(query, start, end, sample(), sample(), sample());
    }

    String sign(String query, long start, long end, int randomOne, int randomTwo, int randomThree) {
        byte[] prefix = prefix(randomOne, randomTwo, randomThree);
        byte[] payload = payload(query == null ? "" : query, start, end);
        byte[] combined = Arrays.copyOf(prefix, prefix.length + payload.length);
        System.arraycopy(payload, 0, combined, prefix.length, payload.length);
        return encode(combined);
    }

    private int sample() {
        return (int) (random.nextDouble() * 10000.0D);
    }

    private static byte[] prefix(int first, int second, int third) {
        byte[] output = new byte[12];
        writeRandom(output, 0, first, 170, 85, 1, 2, 5, 45 & 170);
        writeRandom(output, 4, second, 170, 85, 1, 0, 0, 0);
        writeRandom(output, 8, third, 170, 85, 1, 0, 5, 0);
        return output;
    }

    private static void writeRandom(byte[] output, int offset, int sample,
                                    int highMask, int lowMask, int one, int two, int three, int four) {
        int low = sample & 0xff;
        int high = sample >> 8;
        output[offset] = (byte) ((low & highMask) | one);
        output[offset + 1] = (byte) ((low & lowMask) | two);
        output[offset + 2] = (byte) ((high & highMask) | three);
        output[offset + 3] = (byte) ((high & lowMask) | four);
    }

    private static byte[] payload(String query, long start, long end) {
        byte[] params = doubleSm3((query + "cus").getBytes(StandardCharsets.UTF_8));
        byte[] method = doubleSm3("GETcus".getBytes(StandardCharsets.UTF_8));
        byte[] values = new byte[]{
                44, byteAt(end, 24), 0, 0, 0, 0, 24, params[21], method[21], 0,
                (byte) UA_CODE[23], byteAt(end, 16), 0, 0, 0, 1, 0, (byte) 239,
                params[22], method[22], (byte) UA_CODE[24], byteAt(end, 8), 0, 0, 0, 0,
                byteAt(end, 0), 0, 0, 14, byteAt(start, 24), byteAt(start, 16), 0,
                byteAt(start, 8), byteAt(start, 0), 3, byteAt(end, 32), 1, byteAt(start, 32),
                1, (byte) BROWSER.length, 0, 0, 0
        };
        byte checksum = 0;
        for (byte value : values) checksum ^= value;
        byte[] plaintext = Arrays.copyOf(values, values.length + BROWSER.length + 1);
        System.arraycopy(BROWSER, 0, plaintext, values.length, BROWSER.length);
        plaintext[plaintext.length - 1] = checksum;
        return rc4(plaintext, (byte) 'y');
    }

    private static byte[] doubleSm3(byte[] value) {
        return Sm3.digest(Sm3.digest(value));
    }

    private static byte byteAt(long value, int shift) {
        return (byte) (value >>> shift);
    }

    private static byte[] rc4(byte[] plaintext, byte key) {
        int[] state = new int[256];
        for (int i = 0; i < state.length; i++) state[i] = i;
        int j = 0;
        for (int i = 0; i < state.length; i++) {
            j = (j + state[i] + (key & 0xff)) & 0xff;
            int swap = state[i];
            state[i] = state[j];
            state[j] = swap;
        }
        byte[] output = new byte[plaintext.length];
        int i = 0;
        j = 0;
        for (int index = 0; index < plaintext.length; index++) {
            i = (i + 1) & 0xff;
            j = (j + state[i]) & 0xff;
            int swap = state[i];
            state[i] = state[j];
            state[j] = swap;
            output[index] = (byte) (plaintext[index] ^ state[(state[i] + state[j]) & 0xff]);
        }
        return output;
    }

    private static String encode(byte[] input) {
        StringBuilder result = new StringBuilder((input.length + 2) / 3 * 4);
        for (int offset = 0; offset < input.length; offset += 3) {
            int remaining = input.length - offset;
            int value = (input[offset] & 0xff) << 16;
            if (remaining > 1) value |= (input[offset + 1] & 0xff) << 8;
            if (remaining > 2) value |= input[offset + 2] & 0xff;
            result.append(CHARSET.charAt((value >>> 18) & 63));
            result.append(CHARSET.charAt((value >>> 12) & 63));
            if (remaining > 1) result.append(CHARSET.charAt((value >>> 6) & 63));
            if (remaining > 2) result.append(CHARSET.charAt(value & 63));
        }
        while ((result.length() & 3) != 0) result.append('=');
        return result.toString();
    }
}
