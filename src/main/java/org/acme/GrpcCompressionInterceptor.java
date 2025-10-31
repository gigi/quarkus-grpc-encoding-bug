package org.acme;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.quarkus.grpc.GlobalInterceptor;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.List;

//@ApplicationScoped
//@GlobalInterceptor
public class GrpcCompressionInterceptor implements ServerInterceptor {

  private static final String SERVER_COMPRESSION = "gzip";

  private static final Metadata.Key<String> ACCEPT_ENCODING_KEY =
      Metadata.Key.of("grpc-accept-encoding", Metadata.ASCII_STRING_MARSHALLER);

  private static final String GZIP = "gzip";
  private static final String IDENTITY = "identity";

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

    // Read client's accepted encodings from grpc-accept-encoding header
    String clientAcceptEncoding = headers.get(ACCEPT_ENCODING_KEY);

    // Determine if we should compress based on negotiation
    String selectedCompression = negotiateCompression(SERVER_COMPRESSION, clientAcceptEncoding);

    // Apply the negotiated compression to the call
    if (selectedCompression != null && !IDENTITY.equals(selectedCompression)) {
      call.setCompression(selectedCompression);
      return next.startCall(call, headers);
    }

    return next.startCall(call, headers);
  }

  /**
   * Negotiate compression between server configuration and client preferences.
   *
   * @param serverCompression The server's configured compression (e.g., "gzip" or null)
   * @param clientAcceptEncoding The client's grpc-accept-encoding header value
   * @return The compression to use, or "identity" for no compression
   */
  private String negotiateCompression(String serverCompression, String clientAcceptEncoding) {
    // If server doesn't support compression, use identity
    if (serverCompression == null
        || serverCompression.isEmpty()
        || IDENTITY.equals(serverCompression)) {
      return IDENTITY;
    }

    // If client doesn't specify accepted encodings, don't compress
    if (clientAcceptEncoding == null || clientAcceptEncoding.isEmpty()) {
      return IDENTITY;
    }

    // Parse client's accepted encodings (comma-separated list)
    List<String> clientAcceptedEncodings =
        Arrays.stream(clientAcceptEncoding.split(",")).map(String::trim).toList();

    // If client explicitly only accepts identity, don't compress
    if (clientAcceptedEncodings.contains(IDENTITY) && clientAcceptedEncodings.size() == 1) {
      return IDENTITY;
    }

    // If both server and client support gzip, use it
    if (GZIP.equals(serverCompression) && clientAcceptedEncodings.contains(GZIP)) {
      return GZIP;
    }

    // Default to identity (no compression)
    return IDENTITY;
  }
}
