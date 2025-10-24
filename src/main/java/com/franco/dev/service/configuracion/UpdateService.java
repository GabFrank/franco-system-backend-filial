package com.franco.dev.service.configuracion;

// TODO: Si necesitas usar JSONObject, usa Jackson ObjectMapper en su lugar
// import org.json.JSONArray;
// import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.URL;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class UpdateService {

    private static final String GITHUB_REPO = "GabFrank/franco-system-backend-filial";
    private static final String JAR_NAME = "frc-server.jar";
    private static final String LATEST_RELEASE_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.version}")
    private String appVersion;

    @Autowired
    private Environment env;

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

    public static boolean downloadNewVersion(String urlString, Path homePath) {
        Boolean verifyOk = verifyIfFileExists(urlString, homePath);
        if (!verifyOk) {
            try (InputStream in = new URL(urlString).openStream();
                 ReadableByteChannel rbc = Channels.newChannel(in);
                 FileOutputStream fos = new FileOutputStream(homePath.resolve("frc-server-update.jar").toFile())) {

                long fileSize = new URL(urlString).openConnection().getContentLengthLong();
                ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
                long totalBytesRead = 0;
                int bytesRead;
                while ((bytesRead = rbc.read(buffer)) != -1) {
                    totalBytesRead += bytesRead;
                    ((Buffer) buffer).flip();
                    fos.getChannel().write(buffer);
                    ((Buffer) buffer).clear();
                    int progress = (int) ((totalBytesRead * 100) / fileSize);
                    System.out.print("\rDownload progress: " + progress + "%");
                }

                System.out.println("\nDownload completed successfully.");
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Download failed.");
                return false;
            }
        } else {
            return true;
        }
    }

    private static Boolean verifyIfFileExists(String urlString, Path homePath) {
        Path filePath = homePath.resolve("frc-server-update.jar");

        try {
            long remoteFileSize = new URL(urlString).openConnection().getContentLengthLong();
            if (Files.exists(filePath)) {
                long localFileSize = Files.size(filePath);
                if (localFileSize == remoteFileSize) {
                    System.out.println("The file already exists and has the correct size.");
                    return true;
                } else {
                    System.out.println("The existing file is incomplete. Downloading a new copy...");
                }
            } else {
                System.out.println("The file does not exist. Downloading...");
            }
        } catch (IOException e) {
            System.out.println("Verifiyng failed.");
            return false;
        }
        return false;
    }

    @Scheduled(fixedRate = 300000) // Run every 5 minutes (300,000 milliseconds)
    public void checkForNewRelease() {
//        try {
//            System.out.println("Verificando nueva version");
//            String latestRelease = restTemplate.getForObject(LATEST_RELEASE_URL, String.class);
//            System.out.println("latestRelease: " + latestRelease);
//            if (latestRelease != null) {
//                JSONObject jsonObject = new JSONObject(latestRelease);
//                String latestVersion = jsonObject.optString("tag_name", "");
//                JSONArray assets = jsonObject.optJSONArray("assets");
//                System.out.println("Version instalada: " + appVersion);
//                System.out.println("Version encontrada: " + latestVersion);
//                if (appVersion.equals(latestVersion)) {
//                    System.out.println("Ya posee la ultima version instalada");
//                } else if (!latestVersion.isEmpty() && assets != null) {
//                    System.out.println("Existe una nueva version. Descargando...");
//                    System.out.println(latestVersion);
//
//                    String updateJsonUrl = "https://github.com/GabFrank/franco-system-backend-filial/releases/download/" + latestVersion + "/frc-server.jar";
//                    System.out.println("updateJsonUrl: " + updateJsonUrl);
//                    if (updateJsonUrl != null) {
//                        System.out.println("Iniciando proceso de descarga");
//                        String homePath = env.getProperty("jarPath");
//                        if (homePath != null) {
//                            Path path = Paths.get(homePath);
//                            Boolean ok = downloadNewVersion(updateJsonUrl, path);
//                            if (ok) {
//                                System.out.println("Descargado con exito y se puede actualizar");
//                                String osName = System.getProperty("os.name");
//                                Boolean isWindows = osName.toUpperCase().contains("Windows".toUpperCase());
//                                Boolean isMac = osName.toUpperCase().contains("Mac".toUpperCase());
//                                Boolean isLinux = osName.toUpperCase().contains("nux".toUpperCase());
//
//                                if (isWindows) {
//                                    System.out.println("Is windows");
//                                    try {
//                                        Process process = Runtime.getRuntime().exec(homePath + "/selfUpdate.bat");
//                                        int exitCode = process.waitFor();
//                                        if (exitCode != 0) {
//                                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
//                                                String line;
//                                                while ((line = reader.readLine()) != null) {
//                                                    System.out.println(line);
//                                                }
//                                            }
//                                        }
//                                    } catch (IOException e) {
//                                        e.printStackTrace();
//                                    } catch (InterruptedException e) {
//                                        Thread.currentThread().interrupt();
//                                    }
//                                } else if (isMac) {
//                                    System.out.println("Is mac");
//
//                                } else if (isLinux) {
//                                    System.out.println("Is linux");
//                                    try {
//                                        // Assuming homePath is a String with the directory path where the script is located
//                                        ProcessBuilder processBuilder = new ProcessBuilder("nohup", homePath + "/selfUpdate.sh");
//                                        processBuilder.redirectErrorStream(true); // Redirects error stream to the input stream
//                                        Process process = processBuilder.start();
//
//                                        // Read output from the executed script
//                                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
//                                            String line;
//                                            while ((line = reader.readLine()) != null) {
//                                                System.out.println(line);
//                                            }
//                                        }
//
//                                        // Wait for the process to complete and check for errors
//                                        int exitCode = process.waitFor();
//                                        if (exitCode != 0) {
//                                            System.out.println("Script exited with error code: " + exitCode);
//                                        }
//                                    } catch (IOException e) {
//                                        e.printStackTrace();
//                                    } catch (InterruptedException e) {
//                                        Thread.currentThread().interrupt();
//                                    }
//                                }
//                            } else {
//                                System.out.println("Ocurrio un error y no se puede actualizar");
//                            }
//                        }
//                    }
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            System.out.println("Error al verificar y descargar la nueva versión: " + e.getMessage());
//        }
    }
}
