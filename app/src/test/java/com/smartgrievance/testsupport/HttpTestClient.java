package com.smartgrievance.testsupport;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Map;

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

    public HttpRequest.Builder multipart(String path, Map<String, String> fields,
                                         String fileName, String contentType, byte[] contents) {
        String boundary = "grievance-test-boundary";
        var body = new ArrayList<HttpRequest.BodyPublisher>();
        fields.forEach((name, value) -> body.add(HttpRequest.BodyPublishers.ofString(
                "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                        + value + "\r\n")));
        body.add(HttpRequest.BodyPublishers.ofString("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\""
                + fileName + "\"\r\nContent-Type: " + contentType + "\r\n\r\n"));
        body.add(HttpRequest.BodyPublishers.ofByteArray(contents));
        body.add(HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--\r\n"));
        return request(path).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.concat(body.toArray(HttpRequest.BodyPublisher[]::new)));
    }

    public HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send(request(path).GET().build());
    }

    public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
