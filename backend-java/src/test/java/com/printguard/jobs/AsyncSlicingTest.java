package com.printguard.jobs;

import com.printguard.auth.UserEntity;
import com.printguard.auth.UserRepository;
import com.printguard.jobs.events.ModelUploadedEvent;
import com.printguard.models.GeneratedModelEntity;
import com.printguard.models.GeneratedModelRepository;
import com.printguard.slicer.SlicerService;
import com.printguard.storage.StorageService;
import com.printguard.websocket.WebSocketEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AsyncSlicingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrintJobRepository jobRepository;

    @Autowired
    private GeneratedModelRepository modelRepository;

    @Autowired
    private UserRepository userRepository;

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByUsername("testuser").orElseGet(() -> {
            UserEntity user = new UserEntity();
            user.setUsername("testuser");
            user.setEmail("testuser@printguard.local");
            user.setPassword("pbkdf2_sha256$hashed");
            return userRepository.save(user);
        });
    }

    @Test
    @DisplayName("Async slicing listener processes ModelUploadedEvent and updates status")
    void testAsyncEventListenerExecution() throws Exception {
        PrintJobRepository mockJobRepo = Mockito.mock(PrintJobRepository.class);
        SlicerService mockSlicer = Mockito.mock(SlicerService.class);
        StorageService mockStorage = Mockito.mock(StorageService.class);
        WebSocketEventPublisher mockPublisher = Mockito.mock(WebSocketEventPublisher.class);

        PrintJobEntity entity = new PrintJobEntity();
        entity.setId(101L);
        entity.setStatus("slicing");

        Mockito.when(mockJobRepo.findById(101L)).thenReturn(java.util.Optional.of(entity));
        Mockito.when(mockJobRepo.save(any(PrintJobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Path tempGcode = Files.createTempFile("test_gcode", ".gcode");
        Files.writeString(tempGcode, "G1 X10 Y10 E1\n");

        Mockito.when(mockSlicer.slice(any(), any(), any())).thenReturn(
                new SlicerService.SlicingResult(true, 0, 100L, tempGcode.toString(), "", "", true, 120)
        );

        AsyncSlicingEventListener listener = new AsyncSlicingEventListener(
                mockJobRepo,
                mockSlicer,
                mockStorage,
                mockPublisher
        );

        ModelUploadedEvent event = new ModelUploadedEvent(
                101L,
                55L,
                testUser.getId(),
                "Async Test Model",
                Path.of("model.obj"),
                tempGcode,
                "test.gcode",
                "Original Prusa i3 MK3S & MK3S+"
        );

        listener.handleModelUploadedEvent(event);

        assertEquals("ready_to_print", entity.getStatus());
        assertEquals(100, entity.getProgress());
        Mockito.verify(mockPublisher, Mockito.atLeastOnce()).publishPrintJobStatus(eq(101L), anyString(), anyInt(), anyMap(), anyString());

        Files.deleteIfExists(tempGcode);
    }
}
