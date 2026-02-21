package com.worldsync;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class WorldDownloader {

    private final Map<String, String> fileMap = new HashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<String, String> serverFileMap = new HashMap<>();
    private Gson gson = new Gson();
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldDownloader.class);
    private Path path = null;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private final AtomicInteger counter = new AtomicInteger(0);
    private final AtomicInteger total = new AtomicInteger(0);

    private void checkSessionExists(int worldId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(Config.API_ENDPOINT + "/exists?world=" + worldId))
                .GET()
                .header("User-Agent", Config.USER_AGENT)
                .build();

        HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException("World Does not exist");
        }
    }

    private void loopFiles(File parent) throws IOException, NoSuchAlgorithmException {


        for (File f : Objects.requireNonNull(parent.listFiles())) {


            if (f.isDirectory()) {
                this.loopFiles(f);
                continue;
            }

            FileInputStream fis = new FileInputStream(f.toPath().toString());

            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");

            byte[] buffer = new byte[8192];
            int bytesRead = 0;
            while ((bytesRead = fis.read(buffer)) != -1) {
                sha1.update(buffer, 0, bytesRead);
            }
            fis.close();

            byte[] hashBytes = sha1.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }

            String hash = sb.toString();

            String relativePath = this.path.relativize(f.toPath()).toString().replace("\\", "/");
            this.fileMap.put(relativePath, hash);


        }
    }

    private List<ClientOperation> diffMaps() {

        List<ClientOperation> operations = new ArrayList<>();

        for (String path : this.fileMap.keySet()) {
            if (!this.serverFileMap.containsKey(path)) {
                // path does not exist on server, needs deleting local

                operations.add(new ClientOperation(path, this.fileMap.get(path), ClientFileOperation.DELETE));



                // operations.add(new Operation(path, this.fileMap.get(path), FileOperation.UPLOAD));
            } else if (!(this.fileMap.get(path).equals(this.serverFileMap.get(path)))) {

                // hashes do not match, needs downloading

                operations.add(new ClientOperation(path, this.serverFileMap.get(path), ClientFileOperation.DOWNLOAD));

                // operations.add(new Operation(path, this.fileMap.get(path), FileOperation.UPLOAD));
            }
        }

        for (String path : this.serverFileMap.keySet()) {
            if (!this.fileMap.containsKey(path)) {
                // doesn't exist on the client, needs downloading

                operations.add(new ClientOperation(path, this.serverFileMap.get(path), ClientFileOperation.DOWNLOAD));
            }
        }

        return operations;
    }

    private void downloadFile(int worldId, ClientOperation operation) throws Exception {
        HttpURLConnection httpConn = (HttpURLConnection) new URL(Config.API_ENDPOINT + "/download?world=" + worldId + "&blob=" + operation.hash()).openConnection();
        int responseCode = httpConn.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            InputStream inputStream = httpConn.getInputStream();

            FileOutputStream outputStream = getFileOutputStream(worldId, operation);

            byte[] buffer = new byte[4096];
            int bytesRead = -1;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.close();
            inputStream.close();

            LOGGER.info("Cloned {}", operation.path());


        } else {
            LOGGER.error("Failed. Http response not OK");
        }
    }

    private static @NotNull FileOutputStream getFileOutputStream(int worldId, ClientOperation operation) throws FileNotFoundException {
        String filePathStr = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath() + "/saves/world" + worldId + "/" + operation.path();
        Path filePath = Path.of(filePathStr.replace("\n", "").replace("\r", "")).toAbsolutePath().normalize();
        File path = new File(filePath.toUri());
        File parentDir = path.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs(); // like os.makedirs in Python
        }

        return new FileOutputStream(path);
    }

    private void executeTask(ClientOperation operation, WorkerStatusScreen screen, int worldId, Path worldPath) throws Exception {
        counter.getAndIncrement();

        // screen.progressStage(Component.literal("Updating " + operation.path()));
        // screen.progressStagePercentage(Math.round(100 * ((float) counter.get() / total.get())));

        long threadId = Thread.currentThread().threadId();

        screen.setOverallStatus(String.format(
                "Cloning world... (%s%%) (parallel: using up to 4 threads)",
                Math.round(100 * ((float) counter.get() / total.get()))
        ));
        screen.setWorkerStatus("Worker " + threadId, "Download: " + operation.path());

        if (operation.operation() == ClientFileOperation.DOWNLOAD) {
            this.downloadFile(worldId, operation);
        } else {
            Path absolutePath = worldPath.resolve(operation.path());

            boolean deleteSuccess = absolutePath.toFile().delete();

            if (!deleteSuccess) {
                LOGGER.error("Failed to delete {}", absolutePath.toString());
            }
        }
    }

    public List<String> downloadWorld(int worldId, WorkerStatusScreen screen) throws Exception {
        //screen.progressStart(Component.literal("Cloning world..."));

        //screen.progressStage(Component.literal("Getting metadata..."));

        List<String> errors = new ArrayList<>();

        screen.setOverallStatus("Getting metadata");

        this.checkSessionExists(worldId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(Config.API_ENDPOINT + "/get_data?world=" + worldId))
                .GET()
                .header("User-Agent", Config.USER_AGENT)
                .build();

        HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            errors.add("fetch server data failed: " + res.body());
            throw new RuntimeException("An error occurred while fetching server data: " + res.body());
        }
        DataSyncResponse resJson = gson.fromJson(res.body(), DataSyncResponse.class);

        for (DataSyncFileItemJson item : resJson.data) {
            this.serverFileMap.put(item.path, item.hash);
        }

        screen.setOverallStatus("Computing delta");
        // screen.progressStage(Component.literal("Computing delta..."));

        Path savePath = Minecraft.getInstance().gameDirectory.toPath().resolve("saves");

        List<ClientOperation> operations = new ArrayList<>();

        Path worldPath = savePath.resolve("world" + worldId);

        if (!Files.exists(worldPath)) {
            LOGGER.info("Repository does not exist, needs full cloning. Path not found {}", worldPath);

            operations = this.diffMaps();

        } else {
            LOGGER.info("Repository exists, calculating delta");

            this.path = worldPath;

            this.loopFiles(worldPath.toFile());

            operations = diffMaps();
        }


        total.set(operations.size());
        counter.set(0);


        // Topological sort
        try {
            operations.sort((o1, o2) -> Integer.compare(
                    Path.of(o2.path()).getNameCount(),  // deeper paths first
                    Path.of(o1.path()).getNameCount()
            ));
        } catch (Exception e) {
            errors.add("optional topological sort failed: " + e.getMessage());
            LOGGER.error("optional topological sort failed: ", e);
        }


        List<Callable<Void>> callables = operations.stream().map(op -> (Callable<Void>) () -> {
            try {
                this.executeTask(op, screen, worldId, worldPath);
            } catch (Exception e) {
                errors.add("file download failed: " + op.path() + " error: " + e.getMessage());
                LOGGER.error("An error occurred while downloading file: ", e);
            }

            return null;
        }).toList();

        executor.invokeAll(callables);

        LOGGER.info("All operations complete!");

        return errors;


    }
}
