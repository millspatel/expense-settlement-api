package com.splitwise.security;

/** The authenticated caller, injected into controllers with {@code @AuthenticationPrincipal}. */
public record UserPrincipal(Long id, String email) {
}
