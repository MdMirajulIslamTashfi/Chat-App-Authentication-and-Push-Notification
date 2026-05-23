package com.example.chatApp.repositories;

import com.example.chatApp.entity.ChatMessage;
import com.example.chatApp.entity.User;
import com.example.chatApp.enums.Roles;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    // Custom JPQL Query: Fetches a complete direct dialogue thread between two users.
    // The "OR" logic ensures it pulls messages User A sent to User B AND messages User B sent to User A.
    // All messages in a direct thread between two people (both directions)
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.type = 'DIRECT'
              AND ((m.fromEmail = :a AND m.toEmail = :b)
                OR (m.fromEmail = :b AND m.toEmail = :a))
            ORDER BY m.sentAt ASC
            """)
    List<ChatMessage> findThread(@Param("a") String a, @Param("b") String b);

    // Unread count for a user in a direct thread
    @Query("""
            SELECT COUNT(m) FROM ChatMessage m
            WHERE m.toEmail = :email AND m.fromEmail = :other
              AND m.type = 'DIRECT' AND m.read = false
            """)
    long countUnread(@Param("email") String email, @Param("other") String other);

    // Bulk-updates unread message properties.
    // Mark thread messages as read
    @Modifying
    @Transactional
    @Query("""
            UPDATE ChatMessage m SET m.read = true, m.readAt = :now
            WHERE m.toEmail = :reader AND m.fromEmail = :sender
              AND m.type = 'DIRECT' AND m.read = false
            """)
    void markThreadRead(@Param("reader") String reader,
                        @Param("sender") String sender,
                        @Param("now") LocalDateTime now);

    // Pulls all unread messages addressed to a user across all contacts, sorted newest first
    // All unread messages for a user across all threads
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.toEmail = :email AND m.read = false AND m.type = 'DIRECT'
            ORDER BY m.sentAt DESC
            """)
    List<ChatMessage> findAllUnread(@Param("email") String email);

    // Pulls the absolute total count of all unread messages a user has (powers the top navbar bell icon badge)
    // Total unread count for a user
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.toEmail = :email AND m.read = false AND m.type = 'DIRECT'")
    long countAllUnread(@Param("email") String email);



    // ----------------------- Section: Changing / Additional Queries -----------------------------

    // Pulls every message the user is involved in (either sent or received) ordered newest first
    @Query("""
            SELECT m
            FROM ChatMessage m
            WHERE m.fromEmail = :me
               OR m.toEmail = :me
            ORDER BY m.sentAt DESC
            """)
    List<ChatMessage> findAllRelatedMessages(String me);

    // Derived Query Method: Spring parses this name to automatically write the SQL.
    // Equivalent to: SELECT * FROM chat_message WHERE from_email = ? OR to_email = ? ORDER BY sent_at DESC
    // Needed for conversation sidebar
    List<ChatMessage> findByFromEmailOrToEmailOrderBySentAtDesc(
            String fromEmail,
            String toEmail
    );

    // Derived Query Method: Fetches the concrete unread entity records sent from a specific contact to a specific recipient
    List<ChatMessage> findByToEmailAndFromEmailAndReadFalse(
            String toEmail,
            String fromEmail
    );

    // Derived Query Method: Serves the exact same logic as countUnread() above, but generated automatically by Spring
    // rather than using a hardcoded JPQL string. Returning a primitive 'long' ensures high performance.
    // Needed for unread badges
    long countByToEmailAndFromEmailAndReadFalse(
            String toEmail,
            String fromEmail
    );
}