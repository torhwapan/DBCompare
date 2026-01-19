package com.example.dbvalidator.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 邮箱仿真服务控制器
 * 模拟SMTP服务器接收邮件发送请求
 */
@Slf4j
@RestController
@RequestMapping("/api/mail")
public class MailSimulatorController {

    /**
     * 接收邮件发送请求
     * 仿真SMTP服务，接收javax.mail基于SMTP发出的邮件发送请求
     * 直接返回成功响应
     */
    @PostMapping("/send")
    public MailSendResponse sendMail(@RequestBody MailSendRequest request) {
        log.info("Received mail send request: to={}, subject={}", 
                request.getTo(), request.getSubject());
        
        // 仿真端接收到请求后，什么也不干，直接返回成功
        return MailSendResponse.builder()
                .success(true)
                .message("Email sent successfully (simulated)")
                .requestId(java.util.UUID.randomUUID().toString())
                .timestamp(java.time.LocalDateTime.now().toString())
                .build();
    }

    /**
     * 邮件发送请求模型
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MailSendRequest {
        private String from;
        private String to;
        private String cc;
        private String bcc;
        private String subject;
        private String body;
        private String contentType;
        private java.util.List<Attachment> attachments;
    }

    /**
     * 邮件附件模型
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Attachment {
        private String filename;
        private byte[] content;
        private String contentType;
    }

    /**
     * 邮件发送响应模型
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MailSendResponse {
        private boolean success;
        private String message;
        private String requestId;
        private String timestamp;
    }
}