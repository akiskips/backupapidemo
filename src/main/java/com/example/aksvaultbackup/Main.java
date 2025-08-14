/*
 * MIT License
 * 
 * Copyright (c) 2025 AKS Backup Project
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.example.aksvaultbackup;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

public class Main {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON = MediaType.parse("application/json");

    public static void main(String[] args) throws Exception {
        // Config via env vars for portability in AKS
        String subscriptionId = getenvOrThrow("SUBSCRIPTION_ID");
        String resourceGroup = getenvOrThrow("RESOURCE_GROUP");
        String vaultName = getenvOrThrow("VAULT_NAME");
        String backupInstanceName = getenvOrThrow("BACKUP_INSTANCE_NAME");
        String apiVersion = getenvOrDefault("API_VERSION", "2025-07-01");
        String ruleName = getenvOrDefault("BACKUP_RULE_NAME", "Default");
        String tagName = getenvOrDefault("BACKUP_TAG_NAME", "Default");
        String tagId = getenvOrDefault("BACKUP_TAG_ID", "Default_");

        DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().build();
        String token = credential.getToken(
                new com.azure.core.credential.TokenRequestContext()
                        .addScopes("https://management.azure.com/.default")
        ).block().getToken();

        OkHttpClient http = new OkHttpClient.Builder()
                .callTimeout(Duration.ofMinutes(5))
                .build();

        // Build URL
        String base = String.format(
                "https://management.azure.com/subscriptions/%s/resourceGroups/%s/providers/Microsoft.DataProtection/backupVaults/%s/backupInstances/%s",
                subscriptionId, resourceGroup, vaultName, backupInstanceName
        );
        String url = base + "/backup?api-version=" + apiVersion;

        System.out.println("Request URL: " + url);

        String body = String.format("{\n  \"backupRuleOptions\":  { \n    \"ruleName\": \"%s\"\n    }\n   } ",
                escape(ruleName));

        System.out.println("Request Body: " + body);

        Request req = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, JSON))
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response resp = http.newCall(req).execute()) {
            int code = resp.code();
            String respBody = resp.body() != null ? resp.body().string() : "";
            System.out.println("Status: " + code);
            System.out.println("Headers: " + resp.headers());
            System.out.println("Body: " + respBody);

            // Prefer Azure-AsyncOperation, else Location
            String asyncUrl = Optional.ofNullable(resp.header("Azure-AsyncOperation"))
                    .or(() -> Optional.ofNullable(resp.header("Location")))
                    .orElse(null);

            if (asyncUrl == null || asyncUrl.isEmpty()) {
                System.out.println("No async URL returned. Nothing to poll.");
                return;
            }

            // Poll until Succeeded/Failed
            System.out.println("Polling: " + asyncUrl);
            while (true) {
                String pollJson = get(http, asyncUrl, token);
                String status = extractStatus(pollJson);
                System.out.println("status=" + status);
                if (status == null) {
                    Thread.sleep(5000);
                    continue;
                }
                if (status.equalsIgnoreCase("Succeeded")) {
                    System.out.println("Operation succeeded.");
                    break;
                }
                if (status.equalsIgnoreCase("Failed")) {
                    System.out.println("Operation failed: " + pollJson);
                    break;
                }
                Thread.sleep(5000);
            }
        }
    }

    private static String get(OkHttpClient http, String url, String token) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer " + token)
                .build();
        try (Response resp = http.newCall(req).execute()) {
            return resp.body() != null ? resp.body().string() : "";
        }
    }

    private static String extractStatus(String json) throws IOException {
        if (json == null || json.isEmpty()) return null;
        JsonNode n = MAPPER.readTree(json);
        if (n.hasNonNull("status")) return n.get("status").asText();
        if (n.has("properties") && n.get("properties").hasNonNull("status"))
            return n.get("properties").get("status").asText();
        return null;
    }

    private static String getenvOrThrow(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing env var: " + key);
        return v;
    }

    private static String getenvOrDefault(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
        
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
