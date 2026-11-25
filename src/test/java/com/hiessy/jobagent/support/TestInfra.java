package com.hiessy.jobagent.support;

import com.hiessy.jobagent.posting.PostingSource;
import com.hiessy.jobagent.posting.UrlGuard;
import java.net.InetAddress;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL in a throwaway container (same image as docker-compose), the saved postings in place
 * of the network, and a URL guard that doesn't need DNS. Import it with {@code @Import(TestInfra.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfra {

    /** TEST-NET-3, a public documentation address: what every posting host "resolves" to in tests. */
    private static final byte[] PUBLIC = {(byte) 203, 0, 113, 10};

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));
    }

    @Bean
    @Primary
    PostingSource fixturePostings() {
        return new FixturePostingSource();
    }

    /** IP literals and localhost are still checked for real; named hosts resolve to a public address. */
    @Bean
    @Primary
    UrlGuard offlineUrlGuard() {
        return new UrlGuard(false, host -> host.equalsIgnoreCase("localhost") || host.matches("[\\d.]+|\\[?[0-9a-fA-F:]+]?")
                ? InetAddress.getAllByName(host)
                : new InetAddress[] {InetAddress.getByAddress(host, PUBLIC)});
    }
}
