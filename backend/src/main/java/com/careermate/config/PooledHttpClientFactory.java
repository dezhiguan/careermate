package com.careermate.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * 出站 HTTP 客户端的统一构造入口。
 *
 * <p>原先各处使用 {@code SimpleClientHttpRequestFactory}（底层 {@code HttpURLConnection}）：
 * 它依赖 JVM 全局 keep-alive 缓存，既不校验连接存活也无法配置驱逐，空闲期间被对端或中间网络设备
 * 关闭的连接一旦被复用，首个请求必然 {@code SocketException: Connection reset}。
 *
 * <p>这里改用带以下三重保护的连接池，既保留 keep-alive 的性能收益（外网 HTTPS 调用尤其重要，
 * 关闭复用意味着每次多一轮 TLS 握手），又根除陈旧连接：
 * <ul>
 *   <li>{@code validateAfterInactivity}：连接空闲超过阈值后，取用前先探活；</li>
 *   <li>{@code timeToLive}：为连接设定生命周期上限，避免长期驻留的连接与对端状态失配；</li>
 *   <li>{@code evictIdleConnections} / {@code evictExpiredConnections}：后台线程主动清理。</li>
 * </ul>
 */
public final class PooledHttpClientFactory {

    /** 空闲超过该时长的连接，取用前先探活。 */
    private static final TimeValue VALIDATE_AFTER_INACTIVITY = TimeValue.ofSeconds(5);
    /** 连接生命周期上限，到期强制重建。 */
    private static final TimeValue CONNECTION_TIME_TO_LIVE = TimeValue.ofMinutes(5);
    /** 后台驱逐空闲连接的阈值。 */
    private static final TimeValue EVICT_IDLE_AFTER = TimeValue.ofSeconds(30);

    private static final int MAX_CONN_TOTAL = 50;
    private static final int MAX_CONN_PER_ROUTE = 20;

    private PooledHttpClientFactory() {
    }

    /**
     * @param timeoutMs 连接、读取、以及从池中获取连接的统一超时（毫秒）
     */
    public static ClientHttpRequestFactory create(int timeoutMs) {
        Timeout timeout = Timeout.ofMilliseconds(timeoutMs);

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(timeout)
                .setSocketTimeout(timeout)
                .setValidateAfterInactivity(VALIDATE_AFTER_INACTIVITY)
                .setTimeToLive(CONNECTION_TIME_TO_LIVE)
                .build();

        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(connectionConfig)
                .setMaxConnTotal(MAX_CONN_TOTAL)
                .setMaxConnPerRoute(MAX_CONN_PER_ROUTE)
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(timeout)
                .setResponseTimeout(timeout)
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictIdleConnections(EVICT_IDLE_AFTER)
                .evictExpiredConnections()
                .build();

        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }
}
