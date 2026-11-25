package com.hiessy.jobagent;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JobAgentApplication {

    public static void main(String[] args) {
        // The server works in UTC. PostgreSQL also rejects some legacy zone ids that Windows hands the JVM
        // (America/Buenos_Aires) when the driver connects.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(JobAgentApplication.class, args);
    }
}
