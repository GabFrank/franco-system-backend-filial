package com.franco.dev.service.configuracion;

import lombok.Data;
import org.apache.commons.io.FileUtils;
import org.asynchttpclient.*;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

@Data
@Service
public class DownloadService {

    private org.slf4j.Logger log = LoggerFactory.getLogger(DownloadService.class);

    /**
     * The logger of the class
     */
    public Boolean startDownload(String url, String path) {
        deleteZipFolder(path);
        try {
            log.info("Archivo Descargado");
            log.info("Url: "+ url);
            log.info("Path: "+ path);
            FileUtils.copyURLToFile(new URL(url), new File(path));
            return true;
        } catch (IOException e) {
            log.info("No se pudo descargar el archivo", e.toString());
            deleteZipFolder(path);
            return false;
        }
    }

    public boolean deleteZipFolder(String path) {
        return new File(path).delete();
    }
}