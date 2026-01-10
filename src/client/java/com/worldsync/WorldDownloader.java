package com.worldsync;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorldDownloader {

    private final Map<String, String> fileMap = new HashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<String, String> serverFileMap = new HashMap<>();
    private Gson gson = new Gson();
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldDownloader.class);

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

    public void downloadWorld(int worldId, ProgressScreen screen) throws Exception {
        screen.progressStart(Component.literal("Cloning world..."));

        screen.progressStage(Component.literal("Getting metadata..."));

        this.checkSessionExists(worldId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(Config.API_ENDPOINT + "/get_data?world=" + worldId))
                .header("User-Agent", Config.USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException("An error occurred while fetching server data: " + res.body());
        }





        DataSyncResponse resJson = gson.fromJson(res.body(), DataSyncResponse.class);


        int total = resJson.data.size();
        int counter = 0;


        for (DataSyncFileItemJson item : resJson.data) {
            counter++;

            screen.progressStage(Component.literal("Downloading " + item.path));
            screen.progressStagePercentage(Math.round(100 * ((float) counter / total)));

            this.serverFileMap.put(item.path, item.hash);

            HttpURLConnection httpConn = (HttpURLConnection) new URL(Config.API_ENDPOINT + "/download?world=" + worldId + "&blob=" + item.hash).openConnection();
            int responseCode = httpConn.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = httpConn.getInputStream();

                File path = new File(Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath() + "/saves/world" + worldId + "/" + item.path);
                File parentDir = path.getParentFile();
                if (!parentDir.exists()) {
                    parentDir.mkdirs(); // like os.makedirs in Python
                }

                FileOutputStream outputStream = new FileOutputStream(path);

                byte[] buffer = new byte[4096];
                int bytesRead = -1;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.close();
                inputStream.close();

                LOGGER.info("Cloned {}", item.path);


            } else {
                LOGGER.error("Failed. Http response not OK");
            }
        }

        LOGGER.info("All operations complete!");




    }
}
