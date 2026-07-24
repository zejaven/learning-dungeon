package com.interviewlearning.api;

import com.interviewlearning.bulk.BulkDtos.StartRequest;
import com.interviewlearning.bulk.BulkDtos.StatusResponse;
import com.interviewlearning.bulk.BulkGenerationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST surface of the bulk-generation loop: start with parameters, poll status
 * for the header progress strip, request a graceful stop, dismiss a finished
 * run. The loop itself lives in {@link BulkGenerationService}.
 */
@RestController
@RequestMapping("/api/bulk")
public class BulkGenController {

    private final BulkGenerationService bulk;

    public BulkGenController(BulkGenerationService bulk) {
        this.bulk = bulk;
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody StartRequest request) {
        try {
            return ResponseEntity.ok(bulk.start(request));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return bulk.status();
    }

    @PostMapping("/stop")
    public StatusResponse stop() {
        bulk.requestStop();
        return bulk.status();
    }

    @PostMapping("/dismiss")
    public ResponseEntity<?> dismiss() {
        try {
            bulk.dismiss();
            return ResponseEntity.ok(Map.of("ok", "true"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
