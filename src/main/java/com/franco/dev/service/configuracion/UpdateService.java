package com.franco.dev.service.configuracion;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class UpdateService {

    private static final String GITHUB_REPO = "GabFrank/franco-system-backend-filial";
    private static final String JAR_NAME = "frc-server.jar";
    private static final String LATEST_RELEASE_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.version}")
    private String appVersion;

    @Autowired
    private ApplicationContext context;

    public static String readFileAsString(Path filePath) throws IOException {
        byte[] fileBytes = Files.readAllBytes(filePath);
        return new String(fileBytes, StandardCharsets.UTF_8);
    }

    public static void writeStringToFile(Path filePath, String content) throws IOException {
        byte[] fileBytes = content.getBytes(StandardCharsets.UTF_8);
        Files.write(filePath, fileBytes);
    }

    @Scheduled(fixedRate = 300000) // Run every 5 minutes (300,000 milliseconds)
    public void checkForNewRelease() {
        try {
            System.out.println("Verificando nueva version");
            String latestRelease = restTemplate.getForObject(LATEST_RELEASE_URL, String.class);

            if (latestRelease != null) {
                JSONObject jsonObject = new JSONObject(latestRelease);
                String latestVersion = jsonObject.optString("tag_name", "");
                JSONArray assets = jsonObject.optJSONArray("assets");
                if (appVersion.equals(latestVersion)) {
                    System.out.println("Ya posee la ultima version instalada");
                } else if (!latestVersion.isEmpty() && assets != null) {
                    System.out.println("Existe una nueva version. Descargando...");
                    System.out.println(latestVersion);

                    String updateJsonUrl = getUpdateJsonUrl(assets);
                    if (updateJsonUrl != null) {
                        String updateJson = restTemplate.getForObject(updateJsonUrl, String.class);
                        JSONObject updateJsonObj = new JSONObject(updateJson);
                        JSONArray updateArrayFiles = updateJsonObj.optJSONArray("files");
                        if (updateArrayFiles != null) {
                            for (int x = 0; x < updateArrayFiles.length(); x++) {
                                JSONObject fileObject = updateArrayFiles.getJSONObject(x);
                                String location = fileObject.optString("location", "");
                                String name = fileObject.optString("name", "");
                                Boolean replace = fileObject.optBoolean("replace", true);

                                String fileUrl = getFileUrl(assets, name);
                                if (fileUrl != null) {
                                    Path targetPath = Paths.get(System.getProperty("user.home"), location, name);
                                    downloadFileWithProgress(fileUrl, targetPath, replace);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error al verificar y descargar la nueva versión: " + e.getMessage());
        }
    }

    private String getUpdateJsonUrl(JSONArray assets) {
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String assetName = asset.optString("name", "");
            if ("update.json".equals(assetName)) {
                return asset.optString("browser_download_url", null);
            }
        }
        return null;
    }

    private String getFileUrl(JSONArray assets, String name) {
        for (int j = 0; j < assets.length(); j++) {
            JSONObject asset2 = assets.getJSONObject(j);
            String assetName2 = asset2.optString("name", "");
            if (name.equals(assetName2)) {
                return asset2.optString("browser_download_url", null);
            }
        }
        return null;
    }

    private void mergeFiles(Path targetPath, InputStream inputStream) throws IOException {
        String oldContent = readFileAsString(targetPath);
        String newContent = FileCopyUtils.copyToString(new InputStreamReader(inputStream));
        String mergedContent = oldContent + "\n" + newContent;
        writeStringToFile(targetPath, mergedContent);
    }

    private Boolean downloadFileWithProgress(String fileUrl, Path targetPath, boolean replace) {
        return restTemplate.execute(fileUrl, HttpMethod.GET, null, (ClientHttpResponse response) -> {
            long contentLength = response.getHeaders().getContentLength();
            InputStream inputStream = response.getBody();

            try {
                if (replace) {
                    Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    mergeFiles(targetPath, inputStream);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // This is just a simple example. You can implement a more sophisticated progress display.
            System.out.println("Downloaded: " + targetPath.getFileName() + " (" + contentLength + " bytes)");
            return true;
        });
    }
}
