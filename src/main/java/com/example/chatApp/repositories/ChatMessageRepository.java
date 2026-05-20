package com.example.chatApp.repositories;

import com.example.chatApp.entity.ChatMessage;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

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

    // All unread messages for a user across all threads
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.toEmail = :email AND m.read = false AND m.type = 'DIRECT'
            ORDER BY m.sentAt DESC
            """)
    List<ChatMessage> findAllUnread(@Param("email") String email);

    // Total unread count for a user
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.toEmail = :email AND m.read = false AND m.type = 'DIRECT'")
    long countAllUnread(@Param("email") String email);



    // ------------------------ changing -------------------------------------
    @Query("""
            SELECT m
            FROM ChatMessage m
            WHERE m.fromEmail = :me
               OR m.toEmail = :me
            ORDER BY m.sentAt DESC
            """)
    List<ChatMessage> findAllRelatedMessages(String me);

    // Needed for conversation sidebar
    List<ChatMessage> findByFromEmailOrToEmailOrderBySentAtDesc(
            String fromEmail,
            String toEmail
    );

    List<ChatMessage> findByToEmailAndFromEmailAndReadFalse(
            String toEmail,
            String fromEmail
    );

    // Needed for unread badges
    long countByToEmailAndFromEmailAndReadFalse(
            String toEmail,
            String fromEmail
    );
}