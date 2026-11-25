package com.hiessy.jobagent.posting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiessy.jobagent.guardrails.ToolInputException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlGuardTest {

    private final UrlGuard guard = new UrlGuard(false);

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8080/actuator", "http://127.0.0.1/", "http://10.0.0.5/jobs", "http://192.168.1.1/",
            "http://169.254.169.254/latest/meta-data/", "http://[::1]/", "http://100.64.0.1/", "http://0.0.0.0/",
            "file:///etc/passwd", "ftp://8.8.8.8/x", "javascript:alert(1)", "https://user:pass@8.8.8.8/", "not a url", ""})
    void rejectsLocalPrivateAndNonHttpUrls(String url) {
        assertThatThrownBy(() -> guard.check(url)).isInstanceOf(ToolInputException.class).hasMessageStartingWith("url:");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://8.8.8.8/jobs/1", "http://1.1.1.1/careers?id=4"})
    void acceptsPublicHttpUrls(String url) {
        assertThat(guard.check(url).getHost()).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:9999/posting"})
    void testsCanAllowLocalhost(String url) {
        assertThat(new UrlGuard(true).check(url).getPort()).isEqualTo(9999);
    }
}
