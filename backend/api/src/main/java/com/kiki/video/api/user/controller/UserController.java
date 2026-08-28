package com.kiki.video.api.user.controller;

import com.kiki.video.api.auth.security.AuthPrincipal;
import com.kiki.video.api.user.dto.CurrentUserResponse;
import com.kiki.video.api.user.service.UserService;
import com.kiki.video.common.ApiConstants;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_PREFIX + "/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return userService.getCurrentUser(principal.userId());
    }
}
