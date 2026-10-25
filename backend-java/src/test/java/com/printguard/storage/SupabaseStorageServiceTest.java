package com.printguard.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SupabaseStorageServiceTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private SupabaseStorageService storageService;

    private static final String SUPABASE_URL = "https://mockproject.supabase.co";
    private static final String SERVICE_KEY = "test-service-role-key-secret";
    private static final String BUCKET = "printguard-files";

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        storageService = new SupabaseStorageService(SUPABASE_URL, SERVICE_KEY, BUCKET, restClientBuilder);
    }

    @Test
    @DisplayName("Configuration status: isConfigured returns true when service key is provided")
    void testIsConfigured() {
        assertTrue(storageService.isConfigured());
        assertEquals(BUCKET, storageService.getBucketName());
        assertEquals(SUPABASE_URL, storageService.getSupabaseUrl());

        SupabaseStorageService unconfigured = new SupabaseStorageService(SUPABASE_URL, "", BUCKET);
        assertFalse(unconfigured.isConfigured());
    }

    @Test
    @DisplayName("Privacy Verification: Successfully passes when bucket public=false")
    void testVerifyBucketPrivacySuccess() {
        mockServer.expect(requestTo(SUPABASE_URL + "/storage/v1/bucket/" + BUCKET))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("apikey", SERVICE_KEY))
                .andExpect(header("Authorization", "Bearer " + SERVICE_KEY))
                .andRespond(withSuccess("""
                        {
                            "id": "printguard-files",
                            "name": "printguard-files",
                            "public": false,
                            "created_at": "2026-10-14T00:00:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        boolean isPrivate = storageService.verifyBucketPrivacy();
        assertTrue(isPrivate);
        assertTrue(storageService.isVerifiedPrivate());
        mockServer.verify();
    }

    @Test
    @DisplayName("Privacy Verification: Rejects and throws BucketPrivacyException if bucket is configured as public")
    void testVerifyBucketPrivacyRejectsPublicBucket() {
        mockServer.expect(requestTo(SUPABASE_URL + "/storage/v1/bucket/" + BUCKET))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                            "id": "printguard-files",
                            "name": "printguard-files",
                            "public": true
                        }
                        """, MediaType.APPLICATION_JSON));

        BucketPrivacyException ex = assertThrows(BucketPrivacyException.class, () ->
                storageService.verifyBucketPrivacy());

        assertTrue(ex.getMessage().contains("Security violation"));
        assertTrue(ex.getMessage().contains("PUBLIC"));
        assertFalse(storageService.isVerifiedPrivate());
        mockServer.verify();
    }

    @Test
    @DisplayName("Upload OBJ: Correct endpoint, headers, and Content-Type model/obj")
    void testUploadObjFile() {
        byte[] payload = "v 0 0 0\nv 1 1 1\nf 1 2 3\n".getBytes(StandardCharsets.UTF_8);
        String objectPath = "users/1/models/chess_king.obj";

        mockServer.expect(requestTo(SUPABASE_URL + "/storage/v1/object/" + BUCKET + "/" + objectPath))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", SERVICE_KEY))
                .andExpect(header("Authorization", "Bearer " + SERVICE_KEY))
                .andExpect(header("x-upsert", "true"))
                .andExpect(header("Content-Type", "model/obj"))
                .andExpect(content().bytes(payload))
                .andRespond(withSuccess("""
                        {"Key": "printguard-files/users/1/models/chess_king.obj"}
                        """, MediaType.APPLICATION_JSON));

        String stored = storageService.uploadFile(objectPath, payload, "model/obj");
        assertEquals(objectPath, stored);
        mockServer.verify();
    }

    @Test
    @DisplayName("Upload STL: Correct Content-Type model/stl")
    void testUploadStlFile() {
        byte[] payload = new byte[]{0, 1, 2, 3, 4, 5};
        String objectPath = "users/1/models/part.stl";

        mockServer.expect(requestTo(SUPABASE_URL + "/storage/v1/object/" + BUCKET + "/" + objectPath))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "model/stl"))
                .andExpect(header("x-upsert", "true"))
                .andRespond(withSuccess("""
                        {"Key": "printguard-files/users/1/models/part.stl"}
                        """, MediaType.APPLICATION_JSON));

        String stored = storageService.uploadFile(objectPath, payload, "model/stl");
        assertEquals(objectPath, stored);
        mockServer.verify();
    }

    @Test
    @DisplayName("Upload G-code: Correct Content-Type text/x-gcode")
    void testUploadGCodeFile() {
        byte[] payload = "G28 ; home all\nM104 S210\n".getBytes(StandardCharsets.UTF_8);
        String objectPath = "users/1/gcode/job_42.gcode";

        mockServer.expect(requestTo(SUPABASE_URL + "/storage/v1/object/" + BUCKET + "/" + objectPath))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "text/x-gcode"))
                .andExpect(header("x-upsert", "true"))
                .andRespond(withSuccess("""
                        {"Key": "printguard-files/users/1/gcode/job_42.gcode"}
                        """, MediaType.APPLICATION_JSON));

        String stored = storageService.uploadFile(objectPath, payload, "text/x-gcode");
        assertEquals(objectPath, stored);
        mockServer.verify();
    }

    @Test
    @DisplayName("Download file: Uses authenticated endpoint and returns raw bytes")
    void testDownloadFileSuccess() throws Exception {
        byte[] expected = "v 0 0 0\nv 1 0 0\n".getBytes(StandardCharsets.UTF_8);
        String objectPath = "users/1/models/sample.obj";

        mockServer.expect(requestTo(SUPABASE_URL + "/storage/v1/object/authenticated/" + BUCKET + "/" + objectPath))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("apikey", SERVICE_KEY))
                .andExpect(header("Authorization", "Bearer " + SERVICE_KEY))
                .andRespond(withSuccess(expected, MediaType.APPLICATION_OCTET_STREAM));

        byte[] actual = storageService.downloadFile(objectPath);
        assertArrayEquals(expected, actual);
        mockServer.verify();
    }

    @Test
    @DisplayName("Download file: Missing file (404) throws FileNotFoundException")
    void testDownloadFileMissingThrowsFileNotFound() {
        String objectPath = "users/1/models/nonexistent.obj";

        mockServer.expect(requestTo(SUPABASE_URL + "/storage/v1/object/authenticated/" + BUCKET + "/" + objectPath))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        assertThrows(FileNotFoundException.class, () ->
                storageService.downloadFile(objectPath));
        mockServer.verify();
    }

    @Test
    @DisplayName("Create signed URL: Requests signed URL with expiration")
    void testCreateSignedUrl() {
        String objectPath = "users/1/models/chess.obj";

        mockServer.expect(requestTo(SUPABASE_URL + "/storage/v1/object/sign/" + BUCKET + "/" + objectPath))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"expiresIn\": 3600}"))
                .andRespond(withSuccess("""
                        {"signedURL": "/storage/v1/object/sign/printguard-files/users/1/models/chess.obj?token=xyz"}
                        """, MediaType.APPLICATION_JSON));

        String signedUrl = storageService.createSignedUrl(objectPath, 3600);
        assertTrue(signedUrl.contains(SUPABASE_URL));
        assertTrue(signedUrl.contains("/object/sign/"));
        mockServer.verify();
    }
}
