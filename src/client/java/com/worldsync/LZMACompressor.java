package com.worldsync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZFormatException;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class LZMACompressor {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldSync.MOD_ID);

    public static byte[] compressBytes(byte[] input, int level) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LZMA2Options options = new LZMA2Options();
        options.setPreset(level);

        try (XZOutputStream xzOut = new XZOutputStream(baos, options)) {
            xzOut.write(input);
        }
        return baos.toByteArray();
    }

    public static byte[] decompressBytes(byte[] input) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(input);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (XZInputStream xzIn = new XZInputStream(bais)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = xzIn.read(buffer)) != -1) {
                baos.write(buffer, 0, n);
            }
        } catch (XZFormatException e) {
            LOGGER.warn("Decompress not in XZ format: {}. Returning input directly", e.getMessage());
            return input;
        }
        return baos.toByteArray();
    }
}
