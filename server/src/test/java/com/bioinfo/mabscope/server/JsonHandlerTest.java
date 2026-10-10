/*
 * Copyright 2026 Maxime Ethier - Consultant en Bioinformatique/Biocomputing Consultant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bioinfo.mabscope.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bioinfo.mabscope.server.JsonHandler.BodyTooLargeException;
import com.bioinfo.mabscope.server.JsonHandler.Failure;
import com.bioinfo.mabscope.server.JsonHandler.Kind;
import com.bioinfo.mabscope.server.JsonHandler.Request;
import com.bioinfo.proteomics.parser.ParsingException;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;

/** Unit tests of the base class of the controllers. */
class JsonHandlerTest {

    private enum Color {
        RED
    }

    /** Endless stream of zeros, to exceed the size limits without allocating them. */
    private static final class Zeros extends InputStream {
        @Override
        public int read() {
            return 0;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return len;
        }
    }

    private static Request request(Map<String, String> query, InputStream content) {
        return new Request("POST", "", query, content);
    }

    @Test
    @DisplayName("translates the exceptions into HTTP errors")
    void describe() {
        Failure parsing = JsonHandler.describe(new ParsingException("line 3", "bad"));
        assertEquals(400, parsing.status());
        assertEquals(Kind.PARSING, parsing.kind());
        assertEquals("line 3", parsing.location());

        assertEquals(400, JsonHandler.describe(new IllegalArgumentException("x")).status());
        assertEquals(Kind.TOO_LARGE, JsonHandler.describe(new BodyTooLargeException(1)).kind());
        assertEquals(Kind.UNEXPECTED, JsonHandler.describe(new HttpError(503, "down")).kind());
        assertEquals(Kind.TOO_LARGE, JsonHandler.describe(new HttpError(413, "big")).kind());
        Failure bug = JsonHandler.describe(new IllegalStateException("bug"));
        assertEquals(500, bug.status());
        assertEquals("Internal server error", bug.message());
    }

    @Test
    @DisplayName("answers 500 without details for an unexpected error, and 400 without message")
    void unexpectedErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/bug",
                new JsonHandler("/bug") {
                    @Override
                    protected Object handle(Request request) {
                        throw new IllegalStateException("secret detail");
                    }
                });
        server.createContext(
                "/silent",
                new JsonHandler("/silent") {
                    @Override
                    protected Object handle(Request request) {
                        throw new IllegalArgumentException();
                    }
                });
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var bug =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/bug")).build(),
                            BodyHandlers.ofString());
            assertEquals(500, bug.statusCode());
            assertEquals(
                    "{\"error\":\"Internal server error\",\"kind\":\"UNEXPECTED\"}", bug.body());
            var silent =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/silent")).build(),
                            BodyHandlers.ofString());
            assertEquals("{\"error\":\"Unknown error\",\"kind\":\"INVALID_INPUT\"}", silent.body());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("limits the size of the uploaded files")
    void uploadLimit() throws Exception {
        InputStream small = request(Map.of(), new ByteArrayInputStream(new byte[] {7})).upload();
        assertEquals(7, small.read());
        assertEquals(-1, small.read());

        InputStream endless = request(Map.of(), new Zeros()).upload();
        assertEquals(0, endless.read());
        assertThrows(
                BodyTooLargeException.class,
                () -> endless.transferTo(OutputStream.nullOutputStream()));
    }

    @Test
    @DisplayName("limits the size of the JSON bodies")
    void jsonLimit() {
        HttpError error =
                assertThrows(HttpError.class, () -> request(Map.of(), new Zeros()).json(Map.class));
        assertEquals(413, error.status());
    }

    @Test
    @DisplayName("reads the query parameters")
    void queryParameters() {
        Map<String, String> query = JsonHandler.parseQuery("a=1&&b&a=2&c=%C3%A9+x&color=red");
        assertEquals(Map.of("a", "1", "b", "", "c", "é x", "color", "red"), query);
        assertEquals(Map.of(), JsonHandler.parseQuery(null));
        assertEquals(Map.of(), JsonHandler.parseQuery(""));

        Request request = request(query, InputStream.nullInputStream());
        assertEquals("fallback", request.param("b", "fallback"));
        assertEquals("fallback", request.param("missing", "fallback"));
        assertEquals(Color.RED, request.enumParam("color", Color.class, null));
        assertNull(request.enumParam("missing", Color.class, null));
        assertEquals(
                400,
                assertThrows(HttpError.class, () -> request.enumParam("a", Color.class, null))
                        .status());
    }

    @Test
    @DisplayName("proposes an ASCII and an exact UTF-8 file name")
    void contentDisposition() {
        assertEquals(
                "attachment; filename=\"MabScope-_t_-QC.pdf\";"
                        + " filename*=UTF-8''MabScope-%C3%A9t%C3%A9-QC.pdf",
                JsonHandler.contentDisposition("MabScope-été-QC.pdf"));
    }

    @Test
    @DisplayName("main starts the API on the port given as argument")
    void mainStartsTheServer() throws Exception {
        int port;
        try (var socket = new java.net.ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        ApiServer.main(new String[] {String.valueOf(port)});

        var response =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://127.0.0.1:" + port + "/api/health"))
                                        .build(),
                                BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }
}
