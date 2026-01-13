package com.example.dbvalidator.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 双数据源配置类
 */
@Configuration
public class DataSourceConfig {
    
    /**
     * Oracle 数据源属性
     */
    @Bean
    @ConfigurationProperties("spring.datasource.oracle")
    public DataSourceProperties oracleDataSourceProperties() {
        return new DataSourceProperties();
    }
    
    /**
     * Oracle 数据源
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.oracle.hikari")
    public DataSource oracleDataSource() {
        return oracleDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
    
    /**
     * Oracle JdbcTemplate
     */
    @Bean
    @Primary
    public JdbcTemplate oracleJdbcTemplate() {
        return new JdbcTemplate(oracleDataSource());
    }
    
    /**
     * PostgreSQL 数据源属性
     */
    @Bean
    @ConfigurationProperties("spring.datasource.postgres")
    public DataSourceProperties postgresDataSourceProperties() {
        return new DataSourceProperties();
    }
    
    /**
     * PostgreSQL 数据源
     */
    @Bean
    @ConfigurationProperties("spring.datasource.postgres.hikari")
    public DataSource postgresDataSource() {
        return postgresDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
    
    /**
     * PostgreSQL JdbcTemplate
     */
    @Bean
    public JdbcTemplate postgresJdbcTemplate() {
        return new JdbcTemplate(postgresDataSource());
    }
}
