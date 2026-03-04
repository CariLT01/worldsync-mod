package com.worldsync.functions;

import com.google.gson.Gson;
import com.worldsync.Config;
import com.worldsync.LZMACompressor;
import com.worldsync.Utils;
import com.worldsync.http.ProgressHttpEntityWrapper;
import com.worldsync.responses.CreateSessionResponse;
import com.worldsync.responses.DataSyncFileItemJson;
import com.worldsync.responses.DataSyncResponse;
import com.worldsync.responses.WorldUploadFreeSpaceResponse;
import com.worldsync.screens.WorkerStatusScreen;
import com.worldsync.types.*;
import net.minecraft.client.Minecraft;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
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
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class WorldUploader {

    private final Map<String, String> fileMap = new HashMap<>();
    private Map<String, String> serverFileMap = new HashMap<>();
    private List<Operation> operations = new ArrayList<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private Gson gson = new Gson();
    private Path path;
    private final AtomicInteger filesProcessedCounter = new AtomicInteger(0);
    private final AtomicInteger filesTotal = new AtomicInteger(0);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldUploader.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public WorldUploader() {

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

    private List<Operation> diffMaps() {

        List<Operation> operations = new ArrayList<>();

        for (String path : this.fileMap.keySet()) {
            if (!this.serverFileMap.containsKey(path)) {
                // path does not exist on server, needs adding

                operations.add(new Operation(path, this.fileMap.get(path), FileOperation.UPLOAD));
            } else if (!(this.fileMap.get(path).equals(this.serverFileMap.get(path)))) {

                // hashes do not match, needs updating
                operations.add(new Operation(path, this.fileMap.get(path), FileOperation.UPLOAD));
            }
        }

        for (String path : this.serverFileMap.keySet()) {
            if (!this.fileMap.containsKey(path)) {
                // doesn't exist on the client, needs removing
                operations.add(new Operation(path, this.serverFileMap.get(path), FileOperation.DELETE));
            }
        }

        return operations;
    }

    private CompressionResult processFileForSize(File file) throws Exception {
        byte[] fileBytes = Files.readAllBytes(file.toPath());


        byte[] compressedBytes = LZMACompressor.compressBytes(fileBytes, 6);


        float compressionRatio = (float) compressedBytes.length / fileBytes.length;
        if (compressionRatio < 1) {
            LOGGER.info("compression: {} applied (ratio: {})", file.getPath(), compressionRatio);
            return new CompressionResult(true, compressedBytes);
        } else {
            LOGGER.info("compression: {} reverted (ratio: {})", file.getPath(), compressionRatio);
            return new CompressionResult(false, fileBytes);
        }
    }

    private void uploadFileBatched(List<String> fileLocations, List<String> hashes, int worldId, String taskInfo, int workerId, WorkerStatusScreen screen) throws Exception {


        try (CloseableHttpClient client = HttpClients.createDefault()) {


            HttpPost post = new HttpPost(Config.API_ENDPOINT + "/upload/batch");
            post.addHeader("User-Agent", Config.USER_AGENT);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();

            int index = 0;
            for (String filePath : fileLocations) {
                Path absolutePath = this.path.resolve(filePath);
                File filePathObj = new File(absolutePath.normalize().toString());
                CompressionResult processedFileData = this.processFileForSize(filePathObj);

                screen.setWorkerStatus("Worker " + workerId, String.format("pre-process: %s/%s", index, fileLocations.size()));

                String fileHash = hashes.get(index);

                builder.addBinaryBody("files", processedFileData.processedData(), ContentType.DEFAULT_BINARY, filePathObj.getName());
                builder.addTextBody("client_is_compressed", processedFileData.isCompressed() ? "true" : "false");
                builder.addTextBody("client_hashes", fileHash);
                builder.addTextBody("paths", filePath);

                index++;
            }

            builder.addTextBody("world", String.valueOf(worldId));
            builder.addTextBody("client_compressed", "true");

            // post.setEntity(builder.build());

            HttpEntity originalEntity = builder.build();
            ProgressHttpEntityWrapper trackingEntity = new ProgressHttpEntityWrapper(originalEntity, (written, total) -> {
                Minecraft.getInstance().execute(() -> {
                    String bytesFormattedWritten = Utils.formatBytes(written);
                    String bytesFormattedTotal = Utils.formatBytes(total);
                    float percentageWritten = ((float) written / total) * 100.0f;
                    screen.setWorkerStatus("Worker " + workerId,
                            String.format("%s %s/%s (%.2f)", taskInfo, bytesFormattedWritten, bytesFormattedTotal, percentageWritten));
                });
            });
            post.setEntity(trackingEntity);

            client.execute(post, response -> {
                if (response.getCode() != 200) {
                    throw new RuntimeException("Upload Failed: " + response.getEntity().getContent().toString() + response.getCode());
                }

                // LOGGER.info("Successfully uploaded file: {}", fileLocation);
                LOGGER.info("Upload response: {}", response.getCode());

                return null;
            });
        } catch (IOException e) {
            LOGGER.error("Error while uploading batched: ", e);
            throw new RuntimeException(e);
        }
    }

    private void uploadFile(String fileLocation, int worldId) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            Path absolutePath = this.path.resolve(fileLocation);

            HttpPost post = new HttpPost(Config.API_ENDPOINT + "/upload");
            post.addHeader("User-Agent", Config.USER_AGENT);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addTextBody("path", fileLocation);
            builder.addTextBody("world", String.valueOf(worldId));
            builder.addBinaryBody("file", new File(absolutePath.normalize().toString()));

            post.setEntity(builder.build());

            client.execute(post, response -> {
                if (response.getCode() != 200) {
                    throw new RuntimeException("Returned invalid status: " + response.getEntity().getContent().toString() + response.getCode());
                }

                // LOGGER.info("Successfully uploaded file: {}", fileLocation);

                return null;
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private void deleteFileBatched(List<String> fileLocations, int worldId) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {


            HttpPost post = new HttpPost(Config.API_ENDPOINT + "/remove/batch");
            post.addHeader("User-Agent", Config.USER_AGENT);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();

            for (String filePath : fileLocations) {
                builder.addTextBody("paths", filePath);
            }

            builder.addTextBody("world", String.valueOf(worldId));

            post.setEntity(builder.build());

            client.execute(post, response -> {
                if (response.getCode() != 200) {
                    throw new RuntimeException("Delete Failed: " + response.getEntity().getContent().toString() + response.getCode());
                }

                // LOGGER.info("Successfully uploaded file: {}", fileLocation);

                return null;
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteFile(String fileLocation, int worldId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(Config.API_ENDPOINT + "/remove?world=" + worldId + "&path=" + fileLocation))
                .header("User-Agent", Config.USER_AGENT)
                .DELETE()
                .build();

        HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException("An error occurred while trying to delete: " + res.body());
        }


    }

    private void executeTask(List<Operation> task, int worldId, WorkerStatusScreen screen) throws Exception {
        if (task.isEmpty()) {
            LOGGER.warn("received empty task");
            return;
        }

        long threadId = Thread.currentThread().threadId();




        FileOperation opType = task.getFirst().operation();
        LOGGER.info("Executing task of size: {} type: {}", task.size(), opType);
        String taskInfo = String.format("Executing %s %s tasks", task.size(), opType);
        screen.setWorkerStatus("Worker " + threadId,
               taskInfo);

        List<String> fileLocations = new ArrayList<>();
        for (Operation op : task) {
            fileLocations.add(op.path());
        }

        List<String> fileHashes = new ArrayList<>();
        for (Operation op : task) {
            fileHashes.add(op.hash());
        }

        try {
            if (opType.equals(FileOperation.UPLOAD)) {
                this.uploadFileBatched(fileLocations, fileHashes, worldId, taskInfo, (int) threadId, screen);
            } else if (opType.equals(FileOperation.DELETE)) {
                this.deleteFileBatched(fileLocations, worldId);
            } else {
                LOGGER.warn("Unknown operation type: {}", opType);
            }
        } catch (Exception e) {
            screen.incrementFailuresCounter();
            throw e;
        }



    }


    public List<String> processUpload(int worldId, WorkerStatusScreen screen) throws Exception {
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(Config.API_ENDPOINT + "/get_data?world=" + worldId))
                .GET()
                .header("User-Agent", Config.USER_AGENT)
                .build();

        HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException("An error occurred while fetching server data: " + res.body());
        }





        DataSyncResponse resJson = gson.fromJson(res.body(), DataSyncResponse.class);

        for (DataSyncFileItemJson item : resJson.data) {
            this.serverFileMap.put(item.path, item.hash);
        }

        List<Operation> operationsToDo = this.diffMaps();

        int total = operationsToDo.size();
        int counter = 0;

        // Topological sort
        try {
            operationsToDo.sort((o1, o2) -> Integer.compare(
                    Path.of(o2.path()).getNameCount(),  // deeper paths first
                    Path.of(o1.path()).getNameCount()
            ));
        } catch (Exception e) {
            LOGGER.error("Optional step topological sort failed: ", e);
            errors.add("topological sort failed: " + e.getMessage());
        }


        // Group the delete operations first

        this.filesTotal.set(0);

        List<Operation> deleteOperations = new ArrayList<>();
        List<Operation> uploadOperations = new ArrayList<>();

        for (Operation op : operationsToDo) {
            this.filesTotal.getAndIncrement();
            if (op.operation() == FileOperation.UPLOAD) {
                uploadOperations.add(op);
            } else if (op.operation() == FileOperation.DELETE) {
                deleteOperations.add(op);
            } else {
                LOGGER.warn("Unknown file operation: {}", op.operation());
                errors.add(String.format("skipped file: %s - unknown operation %s", op.path(), op.operation()));
            }
        }

        // Group into tasks

        List<List<Operation>> tasks = new ArrayList<>();


        // Delete operations
        int deletedCounter = 0;
        int MAX_DELETE_PER_REQUEST = 128;
        List<Operation> currentTaskList = new ArrayList<>();
        for (Operation deleteOp : deleteOperations) {
            currentTaskList.add(deleteOp);
            deletedCounter++;
            if (deletedCounter >= MAX_DELETE_PER_REQUEST) {
                tasks.add(currentTaskList);
                currentTaskList = new ArrayList<>();
            }
        }
        if (!currentTaskList.isEmpty()) {
            tasks.add(currentTaskList);
        }
        currentTaskList = new ArrayList<>();

        // Upload operations
        int uploadCounter = 0;
        long uploadSize = 0;
        int MAX_FILES_COUNT = 32;
        long MAX_UPLOAD_BATCH = 5 * 1024 * 1024;

        for (Operation uploadOp : uploadOperations) {
            Path absolutePath = this.path.resolve(uploadOp.path());

            long fileSize = Files.size(absolutePath);
            // If more than MAX_UPLOAD_BATCH

            if (uploadSize + fileSize > MAX_UPLOAD_BATCH && !currentTaskList.isEmpty()) {
                tasks.add(currentTaskList);
                currentTaskList = new ArrayList<>();
                uploadCounter = 0;
                uploadSize = 0;
            }
            currentTaskList.add(uploadOp);
            uploadCounter++;
            uploadSize+=fileSize;
            // if we hit the count or if this single file puts us over the size limit
            if (uploadCounter >= MAX_FILES_COUNT || uploadSize >= MAX_UPLOAD_BATCH) {
                tasks.add(currentTaskList);
                currentTaskList = new ArrayList<>();
                uploadCounter = 0;
                uploadSize = 0;
            }
        }
        if (!currentTaskList.isEmpty()) {
            tasks.add(currentTaskList);
        }

        List<Callable<Void>> callables = tasks.stream().map(task -> (Callable<Void>) () -> {
            try {
                this.executeTask(task, worldId, screen);
            } catch (Exception e) {
                LOGGER.error("Task execution failed: ", e);
                errors.add("task execution failed: " + e.getMessage());
            }
            finally {
                filesProcessedCounter.getAndAdd(task.size());
            }

            return null;
        }).toList();

        // Observer thread

        Thread observerThread = new Thread(() -> {
            while (isRunning.get()) {
                try {
                    // screen.progressStage(Component.literal("Executing tasks..."));
                    // screen.progressStagePercentage(Math.round(100 * ( ((float)filesProcessedCounter.get() / filesTotal.get()))));
                    screen.setOverallStatus(String.format(
                            "Executing tasks... (%s%%) (parallel: using up to 4 threads, batching: 32 files/5 MB)",
                            Math.round(100 * ( ((float)filesProcessedCounter.get() / filesTotal.get())))
                    ));

                    Thread.sleep(250);
                } catch (Exception e) {
                    LOGGER.error("Failed to update screen: ", e);
                }

            }

        });

        isRunning.set(true);
        observerThread.start();

        // Invoke using executor
        List<Future<Void>> futures = executor.invokeAll(callables);

        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                LOGGER.error("Task execution failed:", e);
                errors.add("task execution failed: " + e.getMessage());
                screen.incrementFailuresCounter();
            }
        }

        isRunning.set(false);

        /* for (Operation op : operationsToDo) {
            if (op.operation() == FileOperation.UPLOAD) {
                LOGGER.info("Update file {} (sh1: {})", op.path(), op.hash());

                screen.progressStage(Component.literal("Update file: " + op.path()));
                screen.progressStagePercentage(Math.round(100 * ((float) (counter + 1) / total)));

                this.uploadFile(op.path().replace("\\", "/"), worldId);


            } else {
                LOGGER.info("Remove file {} (sha1: {})", op.path(), op.hash());

                screen.progressStage(Component.literal("Delete file: " + op.path()));
                screen.progressStagePercentage(Math.round(100 * ((float) (counter + 1) / total)));

                this.deleteFile(op.path().replace("\\", "/"), worldId);


            }

            counter++;
        } */

        LOGGER.info("All operations complete");

        return errors;
    }

    private void checkSessionExists(int worldId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(Config.API_ENDPOINT + "/exists?world=" + worldId))
                .header("User-Agent", Config.USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException("World Does not exist");
        }
    }

    private int createSession() throws Exception {
       HttpRequest request = HttpRequest.newBuilder()
               .uri(new URI(Config.API_ENDPOINT + "/create"))
               .POST(HttpRequest.BodyPublishers.ofString("{}"))
               .header("User-Agent", Config.USER_AGENT)
               .build();

       HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString());





       CreateSessionResponse response = gson.fromJson(res.body(), CreateSessionResponse.class);

       if (response == null) {

           LOGGER.warn("JSON returned not valid: {}", res.body());

           throw new RuntimeException("Invalid JSON");
       }

       return response.data;

    }



    private long estimateDirectorySize(File directory) {
        long size = 0;

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else {
                    size += this.estimateDirectorySize(file);
                }
            }
        }

        return size;
    }

    private WorldUploadError getFreeDiskSpace(File directory, WorkerStatusScreen screen) throws Exception {
        LOGGER.info("Estimating directory size");
        screen.setOverallStatus("Computing space requirements...");
        long sizeUsed = this.estimateDirectorySize(directory);
        LOGGER.info("World size: {}", sizeUsed);
        screen.setOverallStatus("Requesting space requirements information...");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(Config.API_ENDPOINT + "/api/get_free_space"))
                .GET()
                .header("User-Agent", Config.USER_AGENT)
                .build();

        HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            LOGGER.error("Unable to validate space requirements: {}", res.body());
            return new WorldUploadError(true, "Unable to validate space requirements.");
        }

        WorldUploadFreeSpaceResponse response = gson.fromJson(res.body(), WorldUploadFreeSpaceResponse.class);

        if (response == null) {
            LOGGER.error("Server returned invalid JSON: {}", res.body());
            return new WorldUploadError(true, "The server has returned data that cannot be parsed by the client.");
        }

        long sizeAvailable = response.data;

        LOGGER.info("Server reported free space: {}", sizeAvailable);

        if (sizeUsed >= sizeAvailable) {
            LOGGER.error("Server does not have enough free space: {} > {}", sizeUsed, sizeAvailable);
            return new WorldUploadError(true, String.format(
                    "Not enough space to store this world. (%d bytes needed > %d bytes available).", sizeUsed, sizeAvailable
            ));
        }

        LOGGER.info("Passed conservative free space check");

        return new WorldUploadError(false, "");
    }


    public WorldUploadResult uploadWorld(File directory, int gameIdRaw, WorkerStatusScreen progressScreen) throws Exception {

        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Passed a File that is not a directory");
        }

        WorldUploadError freeSpaceCheck = this.getFreeDiskSpace(directory, progressScreen);

        if (freeSpaceCheck.hasError()) {
            throw new RuntimeException(freeSpaceCheck.message());
        }

        progressScreen.setOverallStatus("Preparing tasks...");

        int gameId = gameIdRaw;

        if (gameIdRaw != -1 && gameIdRaw != 0) {
            this.checkSessionExists(gameIdRaw);
        } else {
            gameId = this.createSession();
        }

        this.path = directory.toPath();


        progressScreen.setOverallStatus("Enumerating objects...");

        this.loopFiles(directory);

        progressScreen.setOverallStatus("Executing tasks...");

        List<String> errors = this.processUpload(gameId, progressScreen);

        return new WorldUploadResult(gameId, errors);
    }

    public Gson getGson() {
        return gson;
    }

    public void setGson(Gson gson) {
        this.gson = gson;
    }

    public List<Operation> getOperations() {
        return operations;
    }

    public void setOperations(List<Operation> operations) {
        this.operations = operations;
    }

    public Map<String, String> getServerFileMap() {
        return serverFileMap;
    }

    public void setServerFileMap(Map<String, String> serverFileMap) {
        this.serverFileMap = serverFileMap;
    }
}
