package com.test.mini.service;

import com.test.mini.dto.RegisterRequest;
import com.test.mini.dto.UserVO;
import com.test.mini.entity.User;
import com.test.mini.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    
    @Transactional
    public UserVO register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }
        
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(req.getPassword()); // 实际项目要加密！
        user.setEmail(req.getEmail());
        user.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());
        user = userRepository.save(user);
        
        return toVO(user);
    }
    
    public Optional<UserVO> login(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(u -> u.getPassword().equals(password)) // 实际项目要验证加密密码！
                .map(this::toVO);
    }
    
    public Optional<UserVO> findById(Long id) {
        return userRepository.findById(id).map(this::toVO);
    }
    
    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        return vo;
    }
}
