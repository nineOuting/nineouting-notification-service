package com.nineouting.backend.notification.repository;

import com.nineouting.backend.notification.domain.OutgoingMail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutgoingMailRepository extends JpaRepository<OutgoingMail, Long> {
}
