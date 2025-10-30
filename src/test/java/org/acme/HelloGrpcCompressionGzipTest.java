package org.acme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;

import org.junit.jupiter.api.Test;

import jakarta.inject.Singleton;

/**
 * Integration test for gRPC with gzip compression configured on server,
 * and client accepts gzip encoding.
 */
@QuarkusTest
@TestProfile(HelloGrpcCompressionGzipTest.GzipCompressionProfile.class)
class HelloGrpcCompressionGzipTest {

    @GrpcClient("hello-service")
    HelloGrpc helloGrpc;

    @Test
    void testHelloWithGzipCompression() {
        // The client interceptor will add grpc-accept-encoding: gzip header
        HelloReply reply = helloGrpc
                .sayHello(HelloRequest.newBuilder().setName("Neo").build())
                .await()
                .atMost(Duration.ofSeconds(5));
        assertEquals("Hello Neo!", reply.getMessage());

        // Server has gzip compression and client accepts it, so response should be gzip
        String encoding = GzipAcceptEncodingInterceptor.lastEncoding.get();
        assertEquals("gzip", encoding, "Expected gzip encoding when both server and client support it");
    }

    public static class GzipCompressionProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "quarkus.grpc.server.compression", "gzip",
                "quarkus.grpc.clients.hello-service.host", "localhost",
                "quarkus.grpc.clients.hello-service.port", "9000"
            );
        }
    }

    /**
     * Client interceptor that adds grpc-accept-encoding: gzip header.
     */
    @Singleton
    @io.quarkus.grpc.GlobalInterceptor
    public static class GzipAcceptEncodingInterceptor implements ClientInterceptor {

        public static final AtomicReference<String> lastEncoding = new AtomicReference<>();

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method,
                CallOptions callOptions,
                Channel next) {

            return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    // Add grpc-accept-encoding: gzip header
                    Metadata.Key<String> acceptEncodingKey = Metadata.Key.of("grpc-accept-encoding", Metadata.ASCII_STRING_MARSHALLER);
                    headers.put(acceptEncodingKey, "gzip");

                    super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                        @Override
                        public void onHeaders(Metadata headers) {
                            // Capture the grpc-encoding header from the response
                            Metadata.Key<String> encodingKey = Metadata.Key.of("grpc-encoding", Metadata.ASCII_STRING_MARSHALLER);
                            String encoding = headers.get(encodingKey);
                            lastEncoding.set(encoding);
                            super.onHeaders(headers);
                        }
                    }, headers);
                }
            };
        }
    }
}

