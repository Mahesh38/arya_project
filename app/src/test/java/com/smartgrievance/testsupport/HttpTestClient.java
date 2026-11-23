package com.smartgrievance.testsupport;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class HttpTestClient {
    private final URI base;
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .cookieHandler(new CookieManager())
            .build();

    public HttpTestClient(int port) {
        base = URI.create("http://127.0.0.1:" + port);
    }

    public HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(base.resolve(path)).header("Accept", "text/html");
    }

    public HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send(request(path).GET().build());
    }

    public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
