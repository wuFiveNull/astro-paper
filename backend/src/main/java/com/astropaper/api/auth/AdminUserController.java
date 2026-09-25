package com.astropaper.api.auth;

import com.astropaper.api.publiccontent.dto.PageResponseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserManagementService userManagementService;

    public AdminUserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public PageResponseDto<UserSummaryDto> listUsers(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        return userManagementService.listUsers(page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummaryDto createUser(@Valid @RequestBody CreateUserRequest request) {
        return userManagementService.createUser(request);
    }
}
