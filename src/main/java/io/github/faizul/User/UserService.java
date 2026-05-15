package io.github.faizul.User;

import io.github.faizul.User.Dtos.UserDto;
import io.github.faizul.UserRole.UserRepository;
import io.github.faizul.UserRole.UserRoleService;
import io.github.faizul.Utils.Mappings.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserRoleService userRoleService;

    public Mono<UserDto> createUser(User user){
        return userRepository.existsByEmail(user.getEmail())
                .flatMap( exist -> {
                    if (exist){
                        return Mono.error(new RuntimeException("Email already exist"));
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
                        return Mono.error(new RuntimeException("Id Not Found"));
                    }

                    return userRepository.deleteById(id);
                });
    }

    public Flux<UserDto> getAllUsers(){
        return this.userRepository.findAll()
                .map(UserMapper::UserToDto);
    }

    private Mono<UserDto> getUserById(Long id){
        return this.userRepository.existsById(id)
                .flatMap(exist -> {
                    if (!exist){
                        return Mono.error(new RuntimeException("Id Not Found"));
                    }
                    return this.userRepository.findById(id)
                            .map(UserMapper::UserToDto);
                });
    }

}
