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

import com.bioinfo.mabscope.server.controller.AnalysisController;
import com.bioinfo.mabscope.server.controller.ExampleController;
import com.bioinfo.mabscope.server.controller.MsImportController;
import com.bioinfo.mabscope.server.controller.PeptideMapController;
import com.bioinfo.mabscope.server.controller.QuantifyController;
import com.bioinfo.mabscope.server.controller.ReportController;
import com.bioinfo.mabscope.service.AntibodyService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * REST API consumed by the React front-end. Uses the JDK HTTP server and virtual threads.
 *
 * <p>The API is stateless: every request carries the sequences it needs, so that the server keeps
 * no data between requests. Each route is served by a controller of the {@code controller} package,
 * which delegates the work to the shared {@link AntibodyService} model.
 */
public final class ApiServer {

    /** Port used when neither an argument nor the {@code PORT} variable gives one. */
    public static final int DEFAULT_PORT = 7071;

    /** Version of the application, from the Maven build. */
    public static final String VERSION = readVersion();

    private static final Logger LOG = Logger.getLogger(ApiServer.class.getName());

    private ApiServer() {}

    /**
     * Creates a server with every route registered; the caller starts it.
     *
     * @param host address to listen on
     * @param port port to listen on, or 0 for any free port
     * @param service model the controllers delegate to
     * @return the server, not started
     * @throws IOException if the address cannot be bound
     */
    public static HttpServer create(String host, int port, AntibodyService service)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/api/health", new HealthController());
        server.createContext("/api/analysis", new AnalysisController(service));
        server.createContext("/api/peptide-map", new PeptideMapController(service));
        server.createContext("/api/ms/import", new MsImportController(service));
        server.createContext("/api/quantify", new QuantifyController(service));
        server.createContext("/api/report", new ReportController(service, VERSION));
        server.createContext("/api/examples", new ExampleController(service));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        return server;
    }

    /**
     * Starts the API on {@code 127.0.0.1}.
     *
     * @param args optional port number; defaults to the {@code PORT} variable, then 7071
     * @throws IOException if the port is already in use
     */
    public static void main(String[] args) throws IOException {
        String port = args.length > 0 ? args[0] : System.getenv("PORT");
        int portNumber = port == null ? DEFAULT_PORT : Integer.parseInt(port);
        HttpServer server = create("127.0.0.1", portNumber, new AntibodyService());
        server.start();
        int actualPort = server.getAddress().getPort();
        LOG.info(() -> "MabScope API " + VERSION + " on http://127.0.0.1:" + actualPort + "/api");
    }

    private static String readVersion() {
        Properties p = new Properties();
        try (InputStream in = ApiServer.class.getResourceAsStream("server.properties")) {
            p.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return p.getProperty("version");
    }

    /** {@code GET /api/health}: lets clients and the monitoring check that the server runs. */
    private static final class HealthController extends JsonHandler {

        HealthController() {
            super("/api/health");
        }

        @Override
        protected Object handle(Request request) {
            return Map.of("status", "UP", "version", VERSION);
        }
    }
}
