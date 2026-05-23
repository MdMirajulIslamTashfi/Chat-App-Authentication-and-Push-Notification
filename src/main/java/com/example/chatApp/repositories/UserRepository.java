package com.example.chatApp.repositories;

import com.example.chatApp.entity.User;
import com.example.chatApp.enums.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRoleNot(Roles role);
    List<User> findByRole(Roles role);
    Optional<User> findFirstByRole(Roles role);
}