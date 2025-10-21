package com.dcruver.orgroam;

import com.embabel.agent.config.annotation.EnableAgents;
import com.embabel.agent.config.annotation.LocalModels;
import com.embabel.agent.config.annotation.LoggingThemes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Org-Roam Gardener.
 *
 * This is an Embabel GOAP agent service that maintains a local Org/Org-roam
 * knowledge base by auditing and fixing issues related to embeddings,
 * formatting, links, taxonomy, and staleness.
 *
 * All operations are explainable, reversible, and safe by default.
 */
@SpringBootApplication
@EnableAgents(
    loggingTheme = LoggingThemes.STAR_WARS,
    localModels = {LocalModels.OLLAMA}
)
@EnableScheduling
@ConfigurationPropertiesScan
@Slf4j
public class OrgRoamGardenerApplication {

    public static void main(String[] args) {
        log.info("Starting Org-Roam Gardener...");
        SpringApplication.run(OrgRoamGardenerApplication.class, args);
    }
}
