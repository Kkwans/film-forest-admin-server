package com.filmforest.system.controller;

import com.filmforest.common.auth.PasswordService;
import com.filmforest.content.entity.PasswordAlgorithm;
import com.filmforest.content.entity.User;
import com.filmforest.content.entity.UserRole;
import com.filmforest.content.mapper.UserMapper;
import com.filmforest.system.service.DefaultUserListProvisioner;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAdminControllerTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordService passwordService = mock(PasswordService.class);
    private final DefaultUserListProvisioner defaultListProvisioner = mock(DefaultUserListProvisioner.class);
    private final UserAdminController controller = new UserAdminController(
            userMapper, passwordService, defaultListProvisioner);

    @Test
    void createsUserWithTemporaryBcryptPassword() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordService.encode("temporary12")).thenReturn("bcrypt-hash");
        UserAdminController.CreateUserRequest request = new UserAdminController.CreateUserRequest();
        request.setUsername("family");
        request.setPassword("temporary12");

        User user = controller.create(request).getData();

        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getPasswordAlgorithm()).isEqualTo(PasswordAlgorithm.BCRYPT);
        assertThat(user.getMustChangePassword()).isTrue();
        verify(defaultListProvisioner).createFor(user.getId());
    }

    @Test
    void clearsTemporaryFlagWhenAdminChangesOwnPassword() {
        User user = user(7L);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(passwordService.encode("new-secret12")).thenReturn("bcrypt-hash");
        UserAdminController.ResetPasswordRequest body = resetRequest();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 7L);

        controller.resetPassword(7L, body, request);

        assertThat(user.getPasswordAlgorithm()).isEqualTo(PasswordAlgorithm.BCRYPT);
        assertThat(user.getMustChangePassword()).isFalse();
    }

    @Test
    void marksPasswordTemporaryWhenAdminResetsAnotherAccount() {
        User user = user(8L);
        when(userMapper.selectById(8L)).thenReturn(user);
        when(passwordService.encode("new-secret12")).thenReturn("bcrypt-hash");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 7L);

        controller.resetPassword(8L, resetRequest(), request);

        assertThat(user.getMustChangePassword()).isTrue();
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("family");
        return user;
    }

    private UserAdminController.ResetPasswordRequest resetRequest() {
        UserAdminController.ResetPasswordRequest request = new UserAdminController.ResetPasswordRequest();
        request.setNewPassword("new-secret12");
        return request;
    }
}
