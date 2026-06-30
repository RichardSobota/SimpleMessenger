package com.simplemessenger.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.simplemessenger.entity.MessageEntity;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    Page<MessageEntity> findByUserId(UUID userId, Pageable pageable);

    long countByUserId(UUID userId);
}
