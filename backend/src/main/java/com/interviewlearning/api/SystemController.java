package com.interviewlearning.api;

import com.interviewlearning.system.SystemService;
import com.interviewlearning.system.SystemService.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes app self-update/restart to the settings UI. {@code status} is polled
 * (the gear badge and the "Update" count come from it); {@code update} triggers
 * the tray-driven rebuild+restart. See {@link SystemService}.
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemService system;

    public SystemController(SystemService system) {
        this.system = system;
    }

    public record UpdateRequest(boolean pull) {
    }

    @GetMapping("/status")
    public Status status() {
        return system.status();
    }

    /**
     * {@code pull=true} pulls from GitHub then rebuilds; {@code pull=false} just
     * rebuilds from the current local files. Both restart the app.
     */
    @PostMapping("/update")
    public ResponseEntity<Void> update(@RequestBody UpdateRequest req) {
        String error = system.requestUpdate(req.pull());
        if (error != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return ResponseEntity.accepted().build();
    }
}
