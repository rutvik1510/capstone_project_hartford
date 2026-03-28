package org.hartford.eventguard.repo;

import org.hartford.eventguard.entity.ChatMessage;
import org.hartford.eventguard.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserOrderByTimestampAsc(User user);
    List<ChatMessage> findByUserAndEventIdOrderByTimestampAsc(User user, Long eventId);
}
