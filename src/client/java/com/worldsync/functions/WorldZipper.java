package com.worldsync.functions;

import com.worldsync.screens.WorkerStatusScreen;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class WorldZipper {
    public static void zipFolder(Path sourceFolder, Path zipFile, WorkerStatusScreen screen) throws IOException {
        screen.setOverallStatus("Enumerating local objects...");

        long totalBytes = 0;

        try (var walk = Files.walk(sourceFolder)) {
            totalBytes = walk.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        }

        AtomicLong processedBytes = new AtomicLong(0);

        screen.setOverallStatus("Extracting...");

        final long[] lastProgressUpdateTime = {System.currentTimeMillis()};

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)))) {

            long finalTotalBytes = totalBytes;
            Files.walk(sourceFolder).filter(Files::isRegularFile).forEach(path -> {
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

    public static void unzipFolder(Path sourceFile, Path targetFolder, WorkerStatusScreen screen) throws IOException {
        screen.setOverallStatus("Enumerating objects...");

        File targetDir = targetFolder.toFile();
        if (!targetDir.exists()) {
            boolean created = targetDir.mkdirs();
        }

        List<ZipEntry> entries = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(sourceFile.toFile()))) {
            // Count total entries first

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry);
                zis.closeEntry();
            }
        }


        screen.setOverallStatus("Extracting...");

        int total = entries.size();
        int count = 0;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(sourceFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(targetDir, entry.getName());

                if (entry.isDirectory()) {
                    boolean created = outFile.mkdirs();
                } else {
                    // Ensure parent directories exist
                    boolean created = outFile.getParentFile().mkdirs();
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile))) {
                        byte[] buffer = new byte[4096];
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, read);
                        }
                    }
                }
                count++;
                double percent = ((double) count / total) * 100.0;
                screen.setWorkerStatus("Extracting...", String.format("%.2f%%", percent));

                zis.closeEntry();
            }
        }


    }
}
