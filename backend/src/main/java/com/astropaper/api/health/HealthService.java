package com.astropaper.api.health;

import org.springframework.stereotype.Service;

@Service
public class HealthService {

    public HealthResponseDto getHealth() {
        return new HealthResponseDto("UP", "astro-paper-api");
    }
}
