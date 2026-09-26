package vn.ptit.snapvocab.user.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.domain.dto.user.UpdateProfileRequest;
import vn.ptit.snapvocab.user.domain.dto.user.UserDTO;
import vn.ptit.snapvocab.user.domain.mapper.UserMapper;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final AvatarUrlService avatarUrlService;

	public Optional<User> getCurrentUser() {
		return SecurityUtils.getCurrentUserLogin()
				.flatMap(userRepository::findOneWithAuthoritiesByEmailIgnoreCase);
	}

	public Optional<Long> getCurrentUserId() {
		return SecurityUtils.getCurrentUserId()
				.or(() -> getCurrentUser().map(User::getId));
	}

	public UserDTO getProfile(String email) {
		return toDto(findByEmail(email));
	}

	@Transactional
	public UserDTO updateProfile(String email, UpdateProfileRequest request) {
		User user = findByEmail(email);
		userMapper.partialUpdate(user, toDto(request));
		return toDto(userRepository.save(user));
	}

	/** Maps a user for the API, replacing the stored avatar key with a temporary URL. */
	public UserDTO toDto(User user) {
		UserDTO dto = userMapper.toDto(user);
		dto.setAvatarUrl(avatarUrlService.resolve(user.getAvatarUrl()));
		return dto;
	}

	private User findByEmail(String email) {
		return userRepository.findOneByEmailIgnoreCase(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
	}

	private UserDTO toDto(UpdateProfileRequest request) {
		return UserDTO.builder()
				.firstName(request.getFirstName())
				.lastName(request.getLastName())
				.bio(request.getBio())
				.build();
	}
}

