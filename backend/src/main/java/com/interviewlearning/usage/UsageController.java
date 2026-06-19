package com.interviewlearning.usage;

import com.interviewlearning.usage.UsageDtos.UsageSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves selected-provider usage/status for the header meter.
 */
@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final UsageService usage;

    public UsageController(UsageService usage) {
        this.usage = usage;
    }

    @GetMapping
    public UsageSnapshot get(@RequestParam(name = "provider", required = false) String provider) {
        return usage.current(provider);
    }
}
