package com.hiessy.jobagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiessy.jobagent.cv.CvRuleEngine;
import com.hiessy.jobagent.cv.RuleSet;
import java.net.http.HttpClient;
import java.time.Year;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AppConfig {

    @Bean
    RuleSet ruleSet(ObjectMapper mapper) {
        return RuleSet.fromClasspath(mapper);
    }

    @Bean
    CvRuleEngine cvRuleEngine(RuleSet rules, JobAgentProperties props) {
        int year = props.cv().currentYear() > 0 ? props.cv().currentYear() : Year.now().getValue();
        return new CvRuleEngine(rules, year);
    }

    /** Redirects are followed by hand so every hop goes through the URL guard. */
    @Bean
    HttpClient postingHttpClient(JobAgentProperties props) {
        return HttpClient.newBuilder()
                .connectTimeout(props.fetch().timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
