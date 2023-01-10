package com.franco.dev.service.utils;

import org.apache.commons.io.FileUtils;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.stream.Stream;

@Service
public class ImageService {

    public final Logger log = LoggerFactory.getLogger(ImageService.class);

    String userDirectory = System.getProperty("user.home");
    public String storageDirectoryPath = userDirectory + "/FRC/resources/images";
    public String imagePresentaciones = userDirectory + "/FRC/resources/images/productos/presentaciones";
    public String imagePresentacionesThumbPath = userDirectory + "/FRC/resources/images/productos/presentaciones/thumbnails";
    public String storageDirectoryPathReports = userDirectory + "/FRC/resources/reports";
    public String serverPath = userDirectory + "/FRC/frc-server";
    public String appPath = userDirectory + File.separator + "FRC";
    boolean isWindows = true;
    boolean isMac = false;

    public ImageService() {

        String osName = System.getProperty("os.name");

        log.warn(osName);

        isWindows = osName.contains("Windows");
        isMac = osName.contains("Mac");

        if (isWindows) {
            storageDirectoryPath = "C:\\\\FRC\\resources\\images\\";
            storageDirectoryPathReports = "C:\\\\FRC\\resources\\reports\\";
            imagePresentaciones = "C:\\\\FRC\\resources\\images\\productos\\presentaciones\\";
            imagePresentacionesThumbPath = "C:\\\\FRC\\resources\\images\\productos\\presentaciones\\thumbnails\\";
            serverPath = "C:\\\\FRC\\frc-service\\";
            appPath = "C:\\\\FRC\\";
        } else {
            storageDirectoryPath = storageDirectoryPath + "/";
            storageDirectoryPathReports = storageDirectoryPathReports + "/";
            imagePresentaciones = imagePresentaciones + "/";
            imagePresentacionesThumbPath = imagePresentacionesThumbPath + "/";
            serverPath = serverPath + "/";
            appPath = appPath + "/";
        }
    }

    public static MultipartFile converter(String source) {
        String[] charArray = source.split(",");
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] bytes = new byte[0];
        bytes = decoder.decode(charArray[1]);
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] < 0) {
                bytes[i] += 256;
            }
        }
        return Base64Decoder.multipartFile(bytes, charArray[0]);
    }

    public String getResourcesPath() {
        if (isWindows) {
            return "C:\\\\FRC\\resources";
        } else {
            return userDirectory + "/FRC/resources";
        }
    }

    public String getImageWithMediaType(String imageName, String imagePath) {

        byte[] fileContent = new byte[0];
        try {
            String filePath;
            filePath = imagePath + imageName;
            fileContent = FileUtils.readFileToByteArray(new File(filePath));
            String image = Base64.getEncoder().encodeToString(fileContent);
            return "data:image/jpg;base64," + image;
        } catch (IOException e) {
            return null;
        }
    }

    public Boolean saveImageToPath(String imageBase64, String fileName, String imagePath, String imageThumbPath, Boolean thumbnail) throws IOException {
        Boolean res = false;
        String filePath;
        filePath = imagePath + fileName;
        Path path = Paths.get(filePath);
        log.warn("borrando " + path);
        deleteFile(path.toString());
        try {
            Files.copy(converter(imageBase64).getInputStream(), path);
            res = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        saveScaledImage(filePath, imageThumbPath, fileName, 100);
        return res;
    }

    public Boolean deleteFile(String path) throws IOException {
        FileUtils.touch(new File(path));
        File fileToDelete = FileUtils.getFile(path);
        boolean success = FileUtils.deleteQuietly(fileToDelete);
        if (success) {
            log.warn("borrado conn exito");
        } else {
            log.warn("falla al borrar");
        }
        return success;
    }

    public boolean saveScaledImage(String originalFilePath, String path, String fileNameToSave, int width) {
        File input = new File(originalFilePath);
        try {
            BufferedImage image = ImageIO.read(input);
            BufferedImage resized = resize(image, 250, 250);
            File output = new File(path, fileNameToSave);
            deleteFile(output.toString());
            ImageIO.write(resized, "jpeg", output);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private BufferedImage resize(BufferedImage img, int width, int height) {
        if (img.getColorModel().hasAlpha()) {
            img = dropAlphaChannel(img);
        }
        return Scalr.resize(img, 100, 100);
    }

    public Boolean crearThumbs() {
        try (Stream<Path> paths = Files.walk(Paths.get(imagePresentaciones))) {

            paths
                    .filter(Files::isRegularFile)
                    .forEach(f -> {
                        if (f.toString().contains(".jpg")) {
                            System.out.println("convirtiendo imagen: " + f.getFileName().toString());
                            Boolean res = saveScaledImage(f.toString(), imagePresentacionesThumbPath, f.getFileName().toString(), 100);
                            System.out.println(res);
                        }

                    });
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public BufferedImage dropAlphaChannel(BufferedImage src) {
        BufferedImage convertedImg = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        convertedImg.getGraphics().drawImage(src, 0, 0, null);

        return convertedImg;
    }
}
