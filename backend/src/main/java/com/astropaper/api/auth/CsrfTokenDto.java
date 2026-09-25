package com.astropaper.api.auth;

public record CsrfTokenDto(String headerName, String parameterName, String token) {
}
