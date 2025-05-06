package com.franco.dev.service.utils.biometric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

@Service
public class BiometricService implements DisposableBean {

    private final Logger log = LoggerFactory.getLogger(BiometricService.class);

    @Autowired
    private Environment env;

    private volatile boolean isConnected = false; // Flag to indicate connection status
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private int sessionId;
    private int replyId = 0;
    private Thread listeningThread;

    public BiometricService() {
        // Constructor
    }

    public void getRealTimeLogs(String ip, String port) {
        listeningThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (!isConnected) {
                    try {
                        connectAndListen(ip, Integer.parseInt(port));
                        isConnected = true; // Update connection status
                        // Connection logic...
                    } catch (IOException e) {
                        log.error("Connection attempt failed, will retry", e);
                        isConnected = false; // Update connection status
                    }
                }

                try {
                    Thread.sleep(10000); // Adjust the sleep time as needed
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Listening thread interrupted, stopping");
                    break;
                }
            }
        });
        listeningThread.start();
    }

    private void connectAndListen(String ip, int port) throws IOException {
        // Assuming this method now encapsulates the logic to
        // establish the connection and listen for data
        socket = new Socket(ip, port);
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());

        // Send command to register for real-time logs...

        // Listen for real-time logs...
    }

    @Override
    public void destroy() throws Exception {
        if (listeningThread != null && listeningThread.isAlive()) {
            listeningThread.interrupt();
        }
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                log.error("Error closing socket", e);
            }
        }
        isConnected = false; // Update connection status
    }

    private byte[] createCommandHeader() {
        // Construct the TCP packet similar to createTCPHeader
        // Placeholder for actual command bytes
        log.debug("Creating command header");
        return new byte[]{/* constructed command bytes */};
    }

    private void processRealTimeLog(byte[] data) {
        RealTimeLogDecoder.LogData logData = RealTimeLogDecoder.decodeRecordRealTimeLog52(data);
        log.info("Decoded Log Data - UserID: {}, Attendance Time: {}", logData.userId, logData.attTime);
    }


}
