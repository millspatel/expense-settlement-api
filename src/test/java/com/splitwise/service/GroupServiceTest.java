package com.splitwise.service;

import com.splitwise.exception.ApiException;
import com.splitwise.model.ExpenseGroup;
import com.splitwise.model.User;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Unit tests for {@link GroupService}'s authorization and membership rules, with repositories mocked. */
@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groups;
    @Mock
    private UserRepository users;

    @InjectMocks
    private GroupService groupService;

    private static User user(long id, String email) {
        User u = new User("User " + id, email, "hash");
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static ExpenseGroup group(long id, User creator) {
        ExpenseGroup g = new ExpenseGroup("Goa Trip", creator);
        ReflectionTestUtils.setField(g, "id", id);
        return g;
    }

    @Test
    void requireMembershipReturnsGroupForMember() {
        ExpenseGroup trip = group(10L, user(1L, "a@example.com"));
        when(groups.findById(10L)).thenReturn(Optional.of(trip));

        assertThat(groupService.requireMembership(1L, 10L)).isSameAs(trip);
    }

    @Test
    void requireMembershipRejectsNonMemberWith403() {
        when(groups.findById(10L)).thenReturn(Optional.of(group(10L, user(1L, "a@example.com"))));

        assertThatThrownBy(() -> groupService.requireMembership(99L, 10L))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void requireMembershipReturns404ForMissingGroup() {
        when(groups.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.requireMembership(1L, 404L))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void addMemberRejectsDuplicateWith409() {
        User asha = user(1L, "asha@example.com");
        User ravi = user(2L, "ravi@example.com");
        ExpenseGroup trip = group(10L, asha);
        trip.addMember(ravi);
        when(groups.findById(10L)).thenReturn(Optional.of(trip));
        when(users.findByEmailIgnoreCase("ravi@example.com")).thenReturn(Optional.of(ravi));

        assertThatThrownBy(() -> groupService.addMember(1L, 10L, " Ravi@Example.com "))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void addMemberAddsRegisteredUser() {
        User asha = user(1L, "asha@example.com");
        User ravi = user(2L, "ravi@example.com");
        ExpenseGroup trip = group(10L, asha);
        when(groups.findById(10L)).thenReturn(Optional.of(trip));
        when(users.findByEmailIgnoreCase("ravi@example.com")).thenReturn(Optional.of(ravi));

        groupService.addMember(1L, 10L, "ravi@example.com");

        assertThat(trip.hasMember(2L)).isTrue();
    }
}
