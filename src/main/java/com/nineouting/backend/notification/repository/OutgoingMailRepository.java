package com.nineouting.backend.notification.repository;

import com.nineouting.backend.notification.domain.OutgoingMail;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OutgoingMailRepository extends JpaRepository<OutgoingMail, Long> {

    /** 보낼 차례인 메일. 서비스를 여러 개 띄워도 같은 메일을 동시에 보내지 않게 잠그고 건너뛴다 */
    @Query(value = """
            select * from outgoing_mails
            where status = 'PENDING' and next_attempt_at <= :now
            order by id
            limit :limit
            for update skip locked""", nativeQuery = true)
    List<OutgoingMail> findDue(LocalDateTime now, int limit);

    /** 보낸 메일 기록 정리 (인증 코드 등이 들어 있어 오래 두지 않는다) */
    @Modifying
    @Query("delete from OutgoingMail m where m.status = 'SENT' and m.sentAt < :before")
    int deleteSentBefore(LocalDateTime before);
}
