package com.example.dbvalidator.util;

import lombok.extern.slf4j.Slf4j;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * 邮件客户端示例
 * 演示如何使用javax.mail向仿真SMTP服务器发送邮件
 */
@Slf4j
public class MailClientExample {

    /**
     * 发送邮件到仿真SMTP服务器
     * 
     * @param smtpHost SMTP服务器主机
     * @param smtpPort SMTP服务器端口
     * @param from 发件人邮箱
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     */
    public static void sendMailToSimulator(String smtpHost, int smtpPort, 
                                         String from, String to, 
                                         String subject, String content) {
        try {
            // 设置邮件服务器属性
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", String.valueOf(smtpPort));
            props.put("mail.smtp.auth", "false"); // 仿真服务器不需要认证
            props.put("mail.smtp.connectiontimeout", "10000"); // 10秒连接超时
            props.put("mail.smtp.timeout", "30000"); // 30秒读取超时
            
            // 创建会话
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return null; // 仿真服务器不需要认证
                }
            });
            
            // 设置调试模式
            session.setDebug(true);
            
            // 创建邮件消息
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(content);
            
            // 发送邮件
            Transport.send(message);
            
            log.info("邮件已成功发送到仿真SMTP服务器: {}:{} (模拟成功)", smtpHost, smtpPort);
            
        } catch (MessagingException e) {
            log.error("发送邮件到仿真服务器时发生错误", e);
        }
    }
    
    /**
     * 发送测试邮件到本地仿真SMTP服务器
     */
    public static void sendTestMailToLocalSimulator() {
        sendMailToSimulator(
            "localhost", 
            2525, // 我们的仿真SMTP服务器端口
            "sender@example.com", 
            "recipient@example.com", 
            "测试邮件 - SMTP仿真服务", 
            "这是一封发送到SMTP仿真服务的测试邮件。\n\n仿真服务将接收此请求并返回成功状态。"
        );
    }
    
    // 示例用法
    public static void main(String[] args) {
        log.info("演示如何使用javax.mail向仿真SMTP服务器发送邮件...");
        sendTestMailToLocalSimulator();
    }
}