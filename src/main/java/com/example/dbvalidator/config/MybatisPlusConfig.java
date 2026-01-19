package com.example.dbvalidator.config;

import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 * 由于我们主要使用JdbcTemplate，这里简单配置MyBatis-Plus以满足依赖要求
 */
@Configuration
public class MybatisPlusConfig {

    // Oracle 数据源的 MyBatis-Plus 配置
    // 注意：在这个项目中，我们主要是为了满足需求而添加MyBatis-Plus依赖，
    // 实际上我们继续使用JdbcTemplate进行数据库操作
}