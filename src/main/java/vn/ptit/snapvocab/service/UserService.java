package vn.ptit.snapvocab.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.domain.dto.user.UpdateProfileRequest;
import vn.ptit.snapvocab.domain.dto.user.UserDTO;
import vn.ptit.snapvocab.domain.mapper.UserMapper;
import vn.ptit.snapvocab.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserDTO getProfile(String email) {
        User user = userRepository.findOneByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return userMapper.toDto(user);
    }

    @Transactional
    public UserDTO updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findOneByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        userMapper.partialUpdate(user, toDto(request));

        user = userRepository.save(user);
        return userMapper.toDto(user);
    }

    private UserDTO toDto(UpdateProfileRequest request) {
        return UserDTO.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .bio(request.getBio())
                .build();
    }
}
