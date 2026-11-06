package com.rathan.riskmonitor.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void testLoadUserByUsernameFound() {
        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

        User user = new User();
        user.setUsername("analyst1");
        user.setPasswordHash("hashed_pwd");
        user.setRole(Role.ROLE_ANALYST);

        when(userRepository.findByUsername("analyst1")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("analyst1");
        assertNotNull(details);
        assertEquals("analyst1", details.getUsername());
        assertEquals("hashed_pwd", details.getPassword());
        assertTrue(details.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ANALYST")));
    }

    @Test
    void testLoadUserByUsernameNotFound() {
        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("unknown"));
    }
}
