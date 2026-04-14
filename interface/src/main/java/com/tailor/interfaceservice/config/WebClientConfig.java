package com.tailor.interfaceservice.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configures the reactive {@link WebClient} used by {@link com.tailor.interfaceservice.service.BrainClientService}.
 *
 * <p>A dedicated {@code HttpClient} (Reactor Netty) is wired with:
 * <ul>
 *   <li><b>Connect timeout</b> — fail fast if the Brain container is unreachable.</li>
 *   <li><b>Read / Write timeouts</b> — long values chosen deliberately because LLM
 *       streaming responses can take 30–90 s depending on output length.</li>
 *   <li><b>Response timeout</b> — overall per-exchange deadline to prevent leaked connections.</li>
 * </ul>
 */
@Configuration
public class WebClientConfig {

    @Value("${brain.service.base-url}")
    private String brainBaseUrl;

    @Value("${brain.service.tailor-path}")
    private String tailorPath;

    @Bean
    public WebClient brainWebClient(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(Duration.ofMinutes(5))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(300, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(30,  TimeUnit.SECONDS)));

        return builder
                .baseUrl(brainBaseUrl + tailorPath)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
