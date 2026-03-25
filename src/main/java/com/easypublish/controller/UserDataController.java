package com.easypublish.controller;



import com.easypublish.entities.UserDataEntity;
import com.easypublish.repositories.UserDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping("/api/user")
public class UserDataController {

    @Autowired
    private UserDataRepository userDataRepository;

    // Fetch user by address
    @GetMapping("/{address}")
    public ResponseEntity<UserDataEntity> getUserByAddress(@PathVariable String address) {
        Optional<UserDataEntity> userOpt = userDataRepository.findByAddress(address);
        return userOpt
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Update user info (timestamps, etc.)
    @PostMapping("/{address}/update")
    public ResponseEntity<UserDataEntity> updateUser(@PathVariable String address) {

        Optional<UserDataEntity> userOpt = userDataRepository.findByAddress(address);
        UserDataEntity user;

        BigInteger now = BigInteger.valueOf(Instant.now().toEpochMilli());

        if (userOpt.isPresent()) {
            user = userOpt.get();
            user.setLast_seen_at_ms(now);
        } else {
            // If user does not exist yet, create a new one
            user = new UserDataEntity();
            user.setAddress(address); // <-- use normalized address
            user.setFirst_seen_at_ms(now);
            user.setLast_seen_at_ms(now);
        }

        user = userDataRepository.save(user);
        return ResponseEntity.ok(user);
    }


    // ===== NEW: Fetch all objects for a user =====

}
