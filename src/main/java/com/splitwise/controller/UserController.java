package com.splitwise.controller;

import com.splitwise.dto.BalanceDtos.MyBalanceResponse;
import com.splitwise.dto.UserResponse;
import com.splitwise.security.UserPrincipal;
import com.splitwise.service.AuthService;
import com.splitwise.service.BalanceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final AuthService authService;
    private final BalanceService balanceService;

    public UserController(AuthService authService, BalanceService balanceService) {
        this.authService = authService;
        this.balanceService = balanceService;
    }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal UserPrincipal me) {
        return authService.me(me.id());
    }

    /** The caller's net balance in every group, plus the overall total. */
    @GetMapping("/balance")
    public MyBalanceResponse myBalance(@AuthenticationPrincipal UserPrincipal me) {
        return balanceService.myBalance(me.id());
    }
}
