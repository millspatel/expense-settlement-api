package com.splitwise.service;

import com.splitwise.dto.AuthDtos.AuthResponse;
import com.splitwise.dto.AuthDtos.LoginRequest;
import com.splitwise.dto.AuthDtos.SignupRequest;
import com.splitwise.exception.ApiException;
import com.splitwise.model.User;
import com.splitwise.repository.UserRepository;
import com.splitwise.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for {@link AuthService} with the repository, encoder and JWT service mocked out. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private static User user(long id, String email, String hash) {
        User u = new User("Asha", email, hash);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    void signupNormalizesEmailHashesPasswordAndIssuesToken() {
        when(users.existsByEmailIgnoreCase("asha@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });
        when(jwtService.generateToken(7L, "asha@example.com")).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        AuthResponse response = authService.signup(new SignupRequest("  Asha ", "  Asha@Example.COM ", "secret123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("asha@example.com");
        assertThat(captor.getValue().getName()).isEqualTo("Asha");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().id()).isEqualTo(7L);
    }

    @Test
    void signupWithExistingEmailIsRejectedWithoutSavingOrHashing() {
        when(users.existsByEmailIgnoreCase("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest("Asha", "asha@example.com", "secret123")))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);

        verify(users, never()).save(any());
        verifyNoInteractions(passwordEncoder, jwtService);
    }

    @Test
    void loginWithWrongPasswordGetsTheSameErrorAsUnknownEmail() {
        when(users.findByEmailIgnoreCase("asha@example.com"))
                .thenReturn(Optional.of(user(1L, "asha@example.com", "hashed")));
        when(passwordEncoder.matches("wrong-pass", "hashed")).thenReturn(false);
        when(users.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("asha@example.com", "wrong-pass")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid email or password")
                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "whatever")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid email or password");

        verify(jwtService, never()).generateToken(any(), anyString());
    }

    @Test
    void loginWithCorrectPasswordIssuesToken() {
        when(users.findByEmailIgnoreCase("asha@example.com"))
                .thenReturn(Optional.of(user(1L, "asha@example.com", "hashed")));
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(1L, "asha@example.com")).thenReturn("jwt-token");

        AuthResponse response = authService.login(new LoginRequest(" ASHA@example.com", "secret123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().email()).isEqualTo("asha@example.com");
    }
}
