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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.mabscope.examples.ExampleAntibodies;
import com.bioinfo.mabscope.service.AntibodyService;
import com.bioinfo.proteomics.parser.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Functional tests of the REST API: a real server on a free port, called over HTTP as the React
 * client does.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiServerTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private HttpServer server;
    private String base;

    @BeforeAll
    void start() throws IOException {
        server = ApiServer.create("127.0.0.1", 0, new AntibodyService());
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    void stop() {
        server.stop(0);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return CLIENT.send(
                HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return CLIENT.send(
                HttpRequest.newBuilder(URI.create(base + path))
                        .header("Content-Type", "application/json")
                        .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build(),
                BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> postForBytes(String path, String body) throws Exception {
        return CLIENT.send(
                HttpRequest.newBuilder(URI.create(base + path))
                        .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build(),
                BodyHandlers.ofByteArray());
    }

    private static JsonNode json(HttpResponse<String> response) throws IOException {
        return JsonSupport.mapper().readTree(response.body());
    }

    private static ObjectNode trastuzumab() {
        ObjectNode antibody = JsonSupport.mapper().createObjectNode();
        antibody.put("name", "Trastuzumab");
        antibody.put("heavy", ExampleAntibodies.TRASTUZUMAB_HEAVY);
        antibody.put("light", ExampleAntibodies.TRASTUZUMAB_LIGHT);
        return antibody;
    }

    private static ObjectNode body(ObjectNode antibody) {
        ObjectNode body = JsonSupport.mapper().createObjectNode();
        body.set("antibody", antibody);
        return body;
    }

    /** Peptides of the demonstration dataset, as returned by the API. */
    private ArrayNode demoPeptides() throws Exception {
        return (ArrayNode) json(get("/api/examples/demo-results")).get("peptides");
    }

    private static void assertError(HttpResponse<String> response, int status, String kind)
            throws IOException {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(kind, json(response).get("kind").asText());
    }

    @Test
    @DisplayName("GET /api/health answers UP with the version")
    void health() throws Exception {
        HttpResponse<String> response = get("/api/health");

        assertEquals(200, response.statusCode());
        assertEquals("UP", json(response).get("status").asText());
        assertEquals(ApiServer.VERSION, json(response).get("version").asText());
        assertFalse(ApiServer.VERSION.contains("${"), "the version is filled by Maven");
        assertTrue(
                response.headers()
                        .firstValue("Content-Type")
                        .orElseThrow()
                        .startsWith("application/json"));
    }

    @Test
    @DisplayName("OPTIONS answers the CORS preflight of the Vite dev server")
    void corsPreflight() throws Exception {
        HttpResponse<String> response =
                CLIENT.send(
                        HttpRequest.newBuilder(URI.create(base + "/api/analysis"))
                                .method("OPTIONS", BodyPublishers.noBody())
                                .build(),
                        BodyHandlers.ofString());

        assertEquals(204, response.statusCode());
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").get());
        assertEquals(
                "Content-Disposition",
                response.headers().firstValue("Access-Control-Expose-Headers").get());
    }

    @Nested
    @DisplayName("POST /api/analysis")
    class Analysis {

        @Test
        @DisplayName("returns the regions and the liabilities of trastuzumab")
        void trastuzumabAnalysis() throws Exception {
            ObjectNode antibody = trastuzumab();
            antibody.put("scheme", "CHOTHIA");

            JsonNode view = json(post("/api/analysis", antibody.toString()));

            assertEquals("Trastuzumab", view.get("name").asText());
            assertEquals("CHOTHIA", view.get("scheme").asText());
            assertEquals(2, view.get("chains").size());
            JsonNode heavy = view.get("chains").get(0);
            assertEquals("HEAVY", heavy.get("type").asText());
            assertEquals(ExampleAntibodies.TRASTUZUMAB_HEAVY.length(), heavy.get("length").asInt());
            List<String> cdrs = new ArrayList<>();
            heavy.get("regions")
                    .forEach(
                            r -> {
                                if (r.get("cdr").asBoolean()) {
                                    cdrs.add(r.get("sequence").asText());
                                }
                            });
            assertEquals(List.of("GFNIKDT", "YPTNGY", "WGGDGFYAMDY"), cdrs);
            boolean deamidationN55 = false;
            for (JsonNode l : view.get("liabilities")) {
                deamidationN55 |=
                        l.get("chain").asText().equals("Heavy chain")
                                && l.get("position").asInt() == 55
                                && l.get("type").asText().equals("DEAMIDATION")
                                && l.get("inCdr").asBoolean();
            }
            assertTrue(deamidationN55, "N55 of CDR-H2 is a deamidation hotspot");
        }

        @Test
        @DisplayName("accepts FASTA text, a single chain and uses Kabat by default")
        void fastaSingleChain() throws Exception {
            ObjectNode antibody = JsonSupport.mapper().createObjectNode();
            antibody.put("heavy", ">VH\n" + ExampleAntibodies.TRASTUZUMAB_VH + "\n");

            JsonNode view = json(post("/api/analysis", antibody.toString()));

            assertEquals("Antibody", view.get("name").asText());
            assertEquals("KABAT", view.get("scheme").asText());
            assertEquals(1, view.get("chains").size());
            assertEquals("Heavy chain", view.get("chains").get(0).get("name").asText());
        }

        @Test
        @DisplayName("answers 422 when a sequence is not an antibody chain")
        void notAnAntibody() throws Exception {
            HttpResponse<String> response =
                    post("/api/analysis", "{\"name\": \" x \", \"light\": \"MKTAYIAKQRQISFVK\"}");

            assertError(response, 422, "NOT_AN_ANTIBODY");
            assertEquals("Light chain", json(response).get("location").asText());
        }

        @Test
        @DisplayName("answers 400 without sequence, with invalid residues or invalid JSON")
        void invalidInput() throws Exception {
            assertError(post("/api/analysis", "{\"heavy\": \"  \"}"), 400, "INVALID_INPUT");
            assertError(post("/api/analysis", "{\"heavy\": \"EVQL#\"}"), 400, "INVALID_INPUT");
            assertError(post("/api/analysis", "{\"heavy\": "), 400, "INVALID_INPUT");
            assertError(post("/api/analysis", "null"), 400, "INVALID_INPUT");
            assertError(post("/api/analysis", "{\"scheme\": \"IMGT2\"}"), 400, "INVALID_INPUT");
        }

        @Test
        @DisplayName("answers 405 to GET and 404 to a sub-path")
        void wrongRoute() throws Exception {
            assertError(get("/api/analysis"), 405, "INVALID_INPUT");
            assertError(post("/api/analysis/other", "{}"), 404, "NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("POST /api/peptide-map")
    class PeptideMap {

        @Test
        @DisplayName("returns the tryptic peptides with their liabilities and modified forms")
        void trypticMap() throws Exception {
            JsonNode peptides = json(post("/api/peptide-map", body(trastuzumab()).toString()));

            JsonNode cdrH2 = null;
            for (JsonNode p : peptides) {
                if (p.get("sequence").asText().equals("IYPTNGYTR")) {
                    cdrH2 = p;
                }
            }
            assertTrue(cdrH2 != null, "IYPTNGYTR is a tryptic peptide of trastuzumab");
            assertEquals("Heavy chain", cdrH2.get("chain").asText());
            assertTrue(cdrH2.get("coversCdr").asBoolean());
            assertTrue(cdrH2.get("monoisotopicMass").asDouble() > 1000);
            assertFalse(cdrH2.get("forms").isEmpty());
        }

        @Test
        @DisplayName("applies the digestion settings")
        void digestionSettings() throws Exception {
            ObjectNode request = body(trastuzumab());
            request.putObject("digestion")
                    .put("protease", "LYS_C")
                    .put("missedCleavages", 0)
                    .put("minLength", 5)
                    .put("maxLength", 30)
                    .put("alkylatedCysteines", false);

            JsonNode peptides = json(post("/api/peptide-map", request.toString()));

            assertFalse(peptides.isEmpty());
            for (JsonNode p : peptides) {
                String sequence = p.get("sequence").asText();
                assertTrue(sequence.length() >= 5 && sequence.length() <= 30, sequence);
                assertEquals(0, p.get("missedCleavages").asInt());
                assertFalse(p.get("proForma").asText().contains("UNIMOD:4"));
            }
        }

        @Test
        @DisplayName("answers 400 without antibody")
        void missingAntibody() throws Exception {
            HttpResponse<String> response = post("/api/peptide-map", "{}");

            assertError(response, 400, "INVALID_INPUT");
            assertTrue(json(response).get("error").asText().contains("antibody"));
        }
    }

    @Nested
    @DisplayName("POST /api/ms/import")
    class MsImport {

        private static final String MAXQUANT =
                "Modified sequence\tIntensity\tExperiment\tReverse\n"
                        + "_IYPTN(Deamidation (NQ))GYTR_\t1000\tLot A\t\n"
                        + "_IYPTNGYTR_\t9000\tLot A\t\n";

        private HttpResponse<String> upload(String query, String file) throws Exception {
            return CLIENT.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/ms/import" + query))
                            .header("Content-Type", "text/plain")
                            .POST(BodyPublishers.ofString(file, StandardCharsets.UTF_8))
                            .build(),
                    BodyHandlers.ofString());
        }

        @Test
        @DisplayName("detects the format of the file")
        void autoDetection() throws Exception {
            JsonNode view = json(upload("", MAXQUANT));

            assertEquals("MAXQUANT", view.get("format").asText());
            assertEquals("Lot A", view.get("samples").get(0).asText());
            assertEquals(2, view.get("peptides").size());
            assertTrue(view.get("peptides").get(0).get("sequence").asText().contains("["));
            assertTrue(view.get("issues").isEmpty());
        }

        @Test
        @DisplayName("reads the format given as parameter")
        void explicitFormat() throws Exception {
            assertEquals(
                    "MAXQUANT", json(upload("?format=MaxQuant", MAXQUANT)).get("format").asText());
            assertEquals("MAXQUANT", json(upload("?format=auto", MAXQUANT)).get("format").asText());
        }

        @Test
        @DisplayName("answers 400 for an unknown format or a file of another format")
        void invalidFormat() throws Exception {
            assertError(upload("?format=excel", MAXQUANT), 400, "INVALID_INPUT");
            assertError(upload("?format=skyline", MAXQUANT), 400, "PARSING");
            assertError(upload("", "a,b\n1,2\n"), 400, "PARSING");
        }
    }

    @Nested
    @DisplayName("POST /api/quantify")
    class Quantify {

        @Test
        @DisplayName("quantifies the demo lots and flags the stressed sample")
        void demoLots() throws Exception {
            ObjectNode request = body(trastuzumab());
            request.set("peptides", demoPeptides());
            request.put("reference", "Lot A");

            JsonNode view = json(post("/api/quantify", request.toString()));

            assertEquals(3, view.get("samples").size());
            assertFalse(view.get("coverage").isEmpty());
            assertFalse(view.get("sites").isEmpty());
            JsonNode comparison = view.get("comparison");
            assertEquals("Lot A", comparison.get("reference").asText());
            assertEquals(2.0, comparison.get("threshold").asDouble());
            boolean n55Flagged = false;
            for (JsonNode row : comparison.get("rows")) {
                n55Flagged |=
                        row.get("site").get("position").asInt() == 55
                                && row.get("site").get("chain").asText().equals("Heavy chain")
                                && row.get("flagged").asBoolean();
            }
            assertTrue(n55Flagged, "deamidation of N55 rises in the stressed sample");
        }

        @Test
        @DisplayName("omits the comparison without reference and uses the given threshold")
        void comparisonOptions() throws Exception {
            ObjectNode request = body(trastuzumab());
            request.set("peptides", demoPeptides());
            request.put("reference", " ");
            assertTrue(json(post("/api/quantify", request.toString())).get("comparison").isNull());

            request.put("reference", "Lot A");
            request.put("threshold", 50);
            JsonNode comparison = json(post("/api/quantify", request.toString())).get("comparison");
            assertEquals(50.0, comparison.get("threshold").asDouble());
            comparison.get("rows").forEach(r -> assertFalse(r.get("flagged").asBoolean()));
        }

        @Test
        @DisplayName("answers 400 for a missing or malformed peptide")
        void invalidPeptides() throws Exception {
            ObjectNode request = body(trastuzumab());
            ArrayNode peptides = request.putArray("peptides");
            peptides.addObject().put("sequence", "PEPTIDE").put("intensity", 10);
            assertError(post("/api/quantify", request.toString()), 400, "INVALID_INPUT");

            peptides.removeAll();
            peptides.addNull();
            assertError(post("/api/quantify", request.toString()), 400, "INVALID_INPUT");

            peptides.removeAll();
            peptides.addObject().put("sequence", "PEP[").put("sample", "A").put("intensity", 1);
            assertError(post("/api/quantify", request.toString()), 400, "PARSING");

            assertError(post("/api/quantify", "{\"peptides\": []}"), 400, "INVALID_INPUT");
        }

        @Test
        @DisplayName("accepts a request without peptides")
        void noPeptides() throws Exception {
            JsonNode view = json(post("/api/quantify", body(trastuzumab()).toString()));

            assertTrue(view.get("samples").isEmpty());
        }
    }

    @Nested
    @DisplayName("POST /api/report")
    class Report {

        @Test
        @DisplayName("returns the complete report as a PDF attachment")
        void completeReport() throws Exception {
            ObjectNode request = body(trastuzumab());
            request.put("locale", "fr-CA");
            request.put("timeZone", "America/Toronto");
            request.put("analyst", "Maxime Ethier");
            request.put("notes", "Lot release");
            request.putObject("digestion");
            request.set("peptides", demoPeptides());
            request.put("reference", "Lot A");
            request.put("threshold", 3);

            HttpResponse<byte[]> response = postForBytes("/api/report", request.toString());

            assertEquals(200, response.statusCode());
            assertEquals("application/pdf", response.headers().firstValue("Content-Type").get());
            assertTrue(
                    response.headers()
                            .firstValue("Content-Disposition")
                            .get()
                            .contains("MabScope-Trastuzumab-QC.pdf"));
            assertEquals("%PDF", new String(response.body(), 0, 4, StandardCharsets.US_ASCII));
        }

        @Test
        @DisplayName("builds a minimal report with the default locale and time zone")
        void minimalReport() throws Exception {
            HttpResponse<byte[]> minimal =
                    postForBytes("/api/report", body(trastuzumab()).toString());
            assertEquals(200, minimal.statusCode());

            ObjectNode request = body(trastuzumab());
            request.put("locale", " ");
            request.put("timeZone", "");
            request.set("peptides", demoPeptides());
            request.put("reference", "");
            HttpResponse<byte[]> withoutComparison =
                    postForBytes("/api/report", request.toString());
            assertEquals(200, withoutComparison.statusCode());

            request.put("reference", "Lot A");
            request.remove("threshold");
            assertEquals(200, postForBytes("/api/report", request.toString()).statusCode());
        }

        @Test
        @DisplayName("answers 400 for an unknown time zone or a missing antibody")
        void invalidReport() throws Exception {
            ObjectNode request = body(trastuzumab());
            request.put("timeZone", "Mars/Olympus");
            assertError(post("/api/report", request.toString()), 400, "INVALID_INPUT");
            assertError(post("/api/report", "{}"), 400, "INVALID_INPUT");
        }
    }

    @Nested
    @DisplayName("GET /api/examples")
    class Examples {

        @Test
        @DisplayName("lists the example antibodies")
        void exampleAntibodies() throws Exception {
            JsonNode examples = json(get("/api/examples"));

            assertEquals(2, examples.size());
            assertEquals("trastuzumab", examples.get(0).get("id").asText());
            assertEquals(
                    ExampleAntibodies.TRASTUZUMAB_LIGHT, examples.get(0).get("light").asText());
            assertEquals("adalimumab", examples.get(1).get("id").asText());
        }

        @Test
        @DisplayName("returns the demonstration results of three samples")
        void demoResults() throws Exception {
            JsonNode view = json(get("/api/examples/demo-results"));

            assertEquals(3, view.get("samples").size());
            assertTrue(view.get("issues").isEmpty());
            assertTrue(view.get("peptides").size() > 50);
        }

        @Test
        @DisplayName("answers 404 to an unknown example and 405 to POST")
        void wrongRoute() throws Exception {
            assertError(get("/api/examples/unknown"), 404, "NOT_FOUND");
            assertError(post("/api/examples", "{}"), 405, "INVALID_INPUT");
        }
    }
}
