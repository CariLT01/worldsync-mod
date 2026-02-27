package com.worldsync.functions;

import com.worldsync.screens.WorkerStatusScreen;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class WorldZipper {
    public static void zipFolder(Path sourceFolder, Path zipFile, WorkerStatusScreen screen) throws IOException {
        screen.setOverallStatus("Enumerating local objects...");

        long totalBytes = 0;

        try (var walk = Files.walk(sourceFolder)) {
            totalBytes = walk
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        }

        AtomicLong processedBytes = new AtomicLong(0);

        screen.setOverallStatus("Extracting...");

        final long[] lastProgressUpdateTime = {System.currentTimeMillis()};

        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zipFile)))) {

            long finalTotalBytes = totalBytes;
            Files.walk(sourceFolder)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        String entryName = sourceFolder.relativize(path).toString();
                        ZipEntry entry = new ZipEntry(entryName);

                        try (InputStream is = Files.newInputStream(path)) {
                            zos.putNextEntry(entry);

                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                zos.write(buffer, 0, len);

                                long current = processedBytes.addAndGet(len);
                                double percent = (current * 100.0) / finalTotalBytes;

                                if (System.currentTimeMillis() - lastProgressUpdateTime[0] > 250) {
                                    screen.setWorkerStatus("Compressing...", String.format("%s (%.2f%%)", entryName, percent));
                                    lastProgressUpdateTime[0] = System.currentTimeMillis();
                                }



                            }

                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }
}
