package com.gate.pyq.controller;

import com.gate.pyq.dto.AuthRequest;
import com.gate.pyq.dto.AuthResponse;
import com.gate.pyq.entity.User;
import com.gate.pyq.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody AuthRequest request) {
        String cleanUsername = request.getUsername().trim();
        if (userRepository.existsByUsername(cleanUsername)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(AuthResponse.builder()
                            .message("Username already exists")
                            .success(false)
                            .build());
        }

        User user = User.builder()
                .username(cleanUsername)
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);

        return ResponseEntity.ok(AuthResponse.builder()
                .message("User registered successfully")
                .success(true)
                .username(cleanUsername)
                .build());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        String cleanUsername = request.getUsername().trim();
        Optional<User> userOpt = userRepository.findByUsername(cleanUsername);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                return ResponseEntity.ok(AuthResponse.builder()
                        .message("Login successful")
                        .success(true)
                        .username(user.getUsername())
                        .build());
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponse.builder()
                        .message("Invalid username or password")
                        .success(false)
                        .build());
    }
}
