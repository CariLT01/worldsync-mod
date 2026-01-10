package com.worldsync;

import com.google.gson.Gson;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.chat.Component;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.RuntimeErrorException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class WorldUploader {

    private final Map<String, String> fileMap = new HashMap<>();
    private Map<String, String> serverFileMap = new HashMap<>();
    private List<Operation> operations = new ArrayList<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private Gson gson = new Gson();
    private Path path;

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldUploader.class);

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

    public void processUpload(int worldId, ProgressScreen screen) throws Exception {
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

        for (Operation op : operationsToDo) {
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
        }

        LOGGER.info("All operations complete");


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


    public int uploadWorld(File directory, int gameIdRaw, ProgressScreen progressScreen) throws Exception {

        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Passed a File that is not a directory");
        }

        progressScreen.progressStart(Component.literal("Uploading..."));
        progressScreen.progressStage(Component.literal("Preparing..."));

        int gameId = gameIdRaw;

        if (gameIdRaw != -1) {
            this.checkSessionExists(gameIdRaw);
        } else {
            gameId = this.createSession();
        }

        this.path = directory.toPath();



        progressScreen.progressStage(Component.literal("Scanning for files..."));

        this.loopFiles(directory);

        progressScreen.progressStage(Component.literal("Uploading..."));

        this.processUpload(gameId, progressScreen);

        return gameId;
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
