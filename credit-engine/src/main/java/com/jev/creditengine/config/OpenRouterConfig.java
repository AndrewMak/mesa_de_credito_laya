package com.jev.creditengine.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(OpenRouterProperties.class)
public class OpenRouterConfig {

    @Bean
    public WebClient openRouterWebClient(OpenRouterProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(45))
                .doOnConnected(c -> c.addHandlerLast(new ReadTimeoutHandler(45)));
        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(connector)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
