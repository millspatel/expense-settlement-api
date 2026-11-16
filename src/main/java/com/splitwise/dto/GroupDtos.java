package com.splitwise.dto;

import com.splitwise.model.ExpenseGroup;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class GroupDtos {

    private GroupDtos() {
    }

    /** {@code memberEmails} is optional; the creator is always added. */
    public record CreateGroupRequest(@NotBlank @Size(max = 100) String name, List<@Email String> memberEmails) {
    }

    public record AddMemberRequest(@NotBlank @Email String email) {
    }

    public record GroupResponse(Long id, String name, UserResponse createdBy, List<UserResponse> members,
                                Instant createdAt) {

        public static GroupResponse from(ExpenseGroup group) {
            return new GroupResponse(group.getId(), group.getName(), UserResponse.from(group.getCreatedBy()),
                    group.getMembers().stream().map(UserResponse::from).toList(), group.getCreatedAt());
        }
    }
}
