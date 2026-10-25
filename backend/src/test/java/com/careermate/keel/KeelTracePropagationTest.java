package com.careermate.keel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class KeelTracePropagationTest {
    @Test void prefersSemanticParentAndLeavesServiceTraceUntouched() throws Exception {
        var received=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/audit",exchange->{
            received.set(exchange.getRequestHeaders().getFirst("traceparent"));
            byte[] body="{\"accepted\":true}".getBytes();exchange.sendResponseHeaders(200,body.length);
            exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        String parent="00-"+"1".repeat(32)+"-"+"2".repeat(16)+"-01";
        try {
            MDC.put("traceId","service-trace");MDC.put("keelTraceParent",parent);
            new KeelRuntimeClient(new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort(),"service")
                    .post("audit",Map.of("toolName","get_career_tasks"));
            assertThat(received.get()).isEqualTo(parent);
            assertThat(MDC.get("traceId")).isEqualTo("service-trace");
            assertThat(KeelRuntimeClient.validTraceParent("00-"+"0".repeat(32)+"-"+"2".repeat(16)+"-01")).isFalse();
            assertThat(KeelRuntimeClient.validTraceParent("00-"+"1".repeat(32)+"-"+"0".repeat(16)+"-01")).isFalse();
            assertThat(KeelRuntimeClient.validTraceParent("bad")).isFalse();
        }finally{MDC.clear();server.stop(0);}
    }
}
