package com.neopen.ddd.domain.repository;

import com.neopen.ddd.domain.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;

/**
 * <b>
 * <p>
 *
 * @Author neopen
 * @Date 2025/8/27 17:13
 */
@Component
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Modifying
    @Query("""
            UPDATE Order o
               SET o.status = :target,
                   o.preStatus = COALESCE(:preRefund, o.preStatus),
                   o.version = o.version + 1
             WHERE o.id = :id
               AND o.status = :expectedFrom
               AND o.version = :version
            """)
    int updateStatusWithVersion(@Param("id") Long id,
                                @Param("target") String target,
                                @Param("expectedFrom") String expectedFrom,
                                @Param("version") Long version,
                                @Param("preRefund") String preRefund);
}
