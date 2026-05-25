package io.github.faizul.User;

import io.github.faizul.User.Dtos.UserDto;
import io.github.faizul.UserRole.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.NoSuchElementException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserRoleService userRoleService;
    private final PasswordEncoder passwordEncoder;

    public Mono<UserDto> createUser(User user){

        String hashedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashedPassword);

            return userRepository.existsByEmail(user.getEmail())
                .flatMap( exist -> {
                    if (exist){
                        return Mono.error(new IllegalArgumentException("Email already exist"));
                    }

                   return this.userRepository.save(user)
                           .flatMap(saved ->
                                   userRoleService.assignDefaultRole(saved.getId())
                                           .thenReturn(saved))
                           .map(UserMapper::UserToDto);
                });
    }

    public Mono<Void> deleteByID(Long id){
        return userRepository.existsById(id)
                .flatMap(exist -> {
                    if (!exist){
                        return Mono.error(new NoSuchElementException("Id Not Found"));
                    }

                    return userRepository.deleteById(id);
                });
    }

    public Flux<UserDto> getAllUsers(){
        return this.userRepository.findAll()
                .map(UserMapper::UserToDto);
    }

    public Mono<UserDto> getUserById(Long id){
        return this.userRepository.existsById(id)
                .flatMap(exist -> {
                    if (!exist){
                        return Mono.error(new NoSuchElementException("Id Not Found"));
                    }
                    return this.userRepository.findById(id)
                            .map(UserMapper::UserToDto);
                });
    }
}
