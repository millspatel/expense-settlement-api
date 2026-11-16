package com.splitwise.service;

import com.splitwise.dto.GroupDtos.CreateGroupRequest;
import com.splitwise.dto.GroupDtos.GroupResponse;
import com.splitwise.exception.ApiException;
import com.splitwise.model.ExpenseGroup;
import com.splitwise.model.User;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GroupService {

    private final GroupRepository groups;
    private final UserRepository users;

    public GroupService(GroupRepository groups, UserRepository users) {
        this.groups = groups;
        this.users = users;
    }

    @Transactional
    public GroupResponse create(Long callerId, CreateGroupRequest request) {
        User creator = users.findById(callerId).orElseThrow(() -> ApiException.notFound("User not found"));
        ExpenseGroup group = new ExpenseGroup(request.name().trim(), creator);
        if (request.memberEmails() != null) {
            request.memberEmails().forEach(email -> group.addMember(findUserByEmail(email)));
        }
        return GroupResponse.from(groups.save(group));
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> listForUser(Long callerId) {
        return groups.findAllByMemberId(callerId).stream().map(GroupResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse get(Long callerId, Long groupId) {
        return GroupResponse.from(requireMembership(callerId, groupId));
    }

    @Transactional
    public GroupResponse addMember(Long callerId, Long groupId, String email) {
        ExpenseGroup group = requireMembership(callerId, groupId);
        User user = findUserByEmail(email);
        if (group.hasMember(user.getId())) {
            throw ApiException.conflict(user.getEmail() + " is already in this group");
        }
        group.addMember(user);
        return GroupResponse.from(group);
    }

    /** Loads the group, failing with 404 if it doesn't exist or 403 if the caller isn't a member. */
    @Transactional(readOnly = true)
    public ExpenseGroup requireMembership(Long callerId, Long groupId) {
        ExpenseGroup group = groups.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group " + groupId + " not found"));
        if (!group.hasMember(callerId)) {
            throw ApiException.forbidden("You are not a member of this group");
        }
        return group;
    }

    private User findUserByEmail(String email) {
        return users.findByEmailIgnoreCase(AuthService.normalize(email))
                .orElseThrow(() -> ApiException.notFound("No registered user with email " + email));
    }
}
