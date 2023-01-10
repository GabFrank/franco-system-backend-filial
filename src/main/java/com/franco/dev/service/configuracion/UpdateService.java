package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.Actualizacion;
import com.franco.dev.service.rabbitmq.PropagacionService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.utilitarios.InfoTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.update4j.Configuration;
import org.update4j.FileMetadata;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class UpdateService {

    @Autowired
    private DownloadService downloadService;

    @Autowired
    private ImageService imageService;

    @Autowired
    private Environment env;

    @Autowired
    private ActualizacionService actualizacionService;

    private Logger log = LoggerFactory.getLogger(UpdateService.class);

    public Boolean runUpdate(String tag, String fileName) {
        log.info("Consultando permisos");
        if (checkPermissions()) {
            log.info("Permiso concedido");
            return downloadUpdate(env.getProperty("updateRepository") + "/" + tag + "/" + fileName);

        } else {
            log.info("No hay permisos necesarios para realizar la actualizacion");
            return false;
        }
    }

    public Boolean checkForUpdates(){
        log.info("Verificando actualizacion");
        String currentVersion = env.getProperty("app.java.version");
        Actualizacion actualizacion = actualizacionService.findLast();
        if(actualizacion!=null){
            if(!currentVersion.equals(actualizacion.getCurrentVersion())){
                log.info("Actual: "+ currentVersion);
                log.info("Encontrada: "+ actualizacion.getCurrentVersion());
                log.info("Iniciando actualizacion");
                return runUpdate(actualizacion.getCurrentVersion(), actualizacion.getTitle());
            } else {
                log.info("Actualizacion al dia");
            }
        }
        return false;
    }

    private boolean checkPermissions() {
        File updateFolder = new File(imageService.appPath);

        //Check for permission to Create
        try {
            File sample = new File(updateFolder.getAbsolutePath() + File.separator + "empty123123124122354345436.txt");
            /*
             * Create and delete a dummy file in order to check file
             * permissions. Maybe there is a safer way for this check.
             */
            sample.createNewFile();
            sample.delete();
        } catch (IOException e) {
            //Error message shown to user. Operation is aborted
            return false;
        }

        //Also check for Read and Write Permissions
        return updateFolder.canRead() && updateFolder.canWrite();
    }

    private Boolean downloadUpdate(String downloadURL) {
        Process process = null;
        if (InfoTool.isReachableByPing("www.google.com")) {
            log.info("Descargando archivo....");
            Boolean downloadOk = downloadService.startDownload(downloadURL, imageService.appPath + "update" + File.separator + "frc-server.jar");
            if (downloadOk) {
                boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
                if (isWindows) {
                    try {
                        log.info("borrando jar antiguo");
                        deleteFile(imageService.appPath + File.separator + "frc-server" + File.separator + "frc-server.jar");
                        String source = imageService.appPath + File.separator + "update" + File.separator + "frc-server.jar";
                        String dest = imageService.appPath + File.separator + "frc-server" + File.separator + "frc-server.jar";
                        final ArrayList<String> command = new ArrayList<String>();
                        log.info("Ejecutando comandos");
                        command.add("C:\\Windows\\System32\\cmd.exe /c copy " + source + " " + dest);
                        command.add("&&");
                        command.add("net stop frc-server");
                        command.add("&&");
                        command.add("net startt frc-server");
                        final ProcessBuilder builder = new ProcessBuilder(command);
                        builder.start();
                        System.exit(0);

                        return true;
                    } catch (IOException e) {
                        log.info("Ocurrio un problema el ejecutar el archivo");
                        log.info(e.toString());
                    }
                }
            }
        }
        return false;
    }

    private void executeTask(Process p) throws InterruptedException, ExecutionException {
        StreamGobbler streamGobbler =
                new StreamGobbler(p.getInputStream(), System.out::println);
        Future<?> future = Executors.newSingleThreadExecutor().submit(streamGobbler);

        int exitCode = 0;
        exitCode = p.waitFor();
        assert exitCode == 0;
        future.get();
    }

    public boolean deleteFile(String path) {
        return new File(path).delete();
    }

    public void doUpdate(String url){
        Configuration.Builder cb = Configuration.builder()

                // base URI from where to download, overridable in
                // each individual file setting
                .baseUri(url)

                // base path where to save on client machine, overridable in
                // each individual file setting
                .basePath(imageService.appPath+File.separator+"update")

                // List this property
                .property("app.name", "FrancoSystemsApplication")

                // Automatically resolves system property
                .property("user.location", imageService.appPath+File.separator+"frc-server")

        // List this file, uri and path are same as filename
        // Read metadata from real file on dev machine
        // Will be dynamically loaded on the modulepath
             .file(FileMetadata.readFrom("frc-server.jar")
                .modulepath());


// Once all settings are set, let's build it
        Configuration config = cb.build();
    }

}