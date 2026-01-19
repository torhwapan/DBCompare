package com.example.dbvalidator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 邮箱仿真服务
 * 实现一个简单的SMTP服务器，接收javax.mail基于SMTP发出的邮件发送请求
 * 仿真端接收到请求后，什么也不干，直接返回成功
 */
@Slf4j
@Service
public class MailSimulationService {

    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private volatile boolean running = false;
    
    // SMTP默认端口
    private static final int SMTP_PORT = 2525; // 使用非标准端口避免权限问题
    
    @PostConstruct
    public void startSmtpServer() {
        try {
            serverSocket = new ServerSocket(SMTP_PORT);
            executorService = Executors.newCachedThreadPool();
            running = true;
            
            log.info("SMTP仿真服务器已启动，监听端口: {}", SMTP_PORT);
            
            // 启动监听线程
            Thread listenerThread = new Thread(this::listenForConnections, "SMTP-Listener");
            listenerThread.setDaemon(true);
            listenerThread.start();
            
        } catch (Exception e) {
            log.error("启动SMTP仿真服务器失败", e);
        }
    }
    
    @PreDestroy
    public void stopSmtpServer() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (executorService != null) {
                executorService.shutdown();
            }
            log.info("SMTP仿真服务器已停止");
        } catch (Exception e) {
            log.error("停止SMTP仿真服务器时发生错误", e);
        }
    }
    
    private void listenForConnections() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                log.debug("收到SMTP连接请求");
                
                // 交给线程池处理
                executorService.submit(() -> handleClient(clientSocket));
                
            } catch (Exception e) {
                if (running) {
                    log.error("接受SMTP连接时发生错误", e);
                }
            }
        }
    }
    
    private void handleClient(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            
            // 发送SMTP欢迎消息
            sendResponse(writer, "220 SMTP Simulator Ready");
            
            String line;
            boolean expectingData = false;
            StringBuilder emailData = new StringBuilder();
            
            while ((line = reader.readLine()) != null && running) {
                log.debug("收到SMTP命令: {}", line);
                
                if (expectingData) {
                    if (line.equals(".")) {
                        // 数据结束
                        expectingData = false;
                        log.info("收到邮件数据:\n{}", emailData.toString());
                        
                        // 返回成功响应
                        sendResponse(writer, "250 OK: Message accepted for delivery (simulated)");
                        emailData.setLength(0); // 清空数据
                    } else {
                        // 收集邮件数据
                        emailData.append(line).append("\n");
                    }
                } else {
                    // 处理SMTP命令
                    String command = line.substring(0, Math.min(4, line.length())).toUpperCase().trim();
                    
                    switch (command) {
                        case "HELO":
                        case "EHLO":
                            sendResponse(writer, "250 Hello, this is a SMTP simulator");
                            break;
                        case "MAIL":
                            sendResponse(writer, "250 OK");
                            break;
                        case "RCPT":
                            sendResponse(writer, "250 OK");
                            break;
                        case "DATA":
                            expectingData = true;
                            sendResponse(writer, "354 Start mail input; end with <CRLF>.<CRLF>");
                            break;
                        case "QUIT":
                            sendResponse(writer, "221 Bye");
                            return;
                        case "RSET":
                            sendResponse(writer, "250 OK");
                            break;
                        case "NOOP":
                            sendResponse(writer, "250 OK");
                            break;
                        default:
                            sendResponse(writer, "250 OK"); // 默认接受所有命令
                            break;
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("处理SMTP客户端连接时发生错误", e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                log.error("关闭SMTP客户端连接时发生错误", e);
            }
        }
    }
    
    private void sendResponse(PrintWriter writer, String response) {
        writer.println(response);
        writer.flush();
        log.debug("发送SMTP响应: {}", response);
    }
    
    public int getPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : SMTP_PORT;
    }
    
    public boolean isRunning() {
        return running && serverSocket != null && !serverSocket.isClosed();
    }
}