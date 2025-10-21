package com.dcruver.orgroam.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for SQLite data source.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(@Value("${gardener.embeddings-db}") String embeddingsDb) throws Exception {
        // Ensure parent directory exists
        Path dbPath = Paths.get(embeddingsDb.replace("${user.home}", System.getProperty("user.home")));
        if (dbPath.getParent() != null) {
            Files.createDirectories(dbPath.getParent());
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());

        return dataSource;
    }
}
