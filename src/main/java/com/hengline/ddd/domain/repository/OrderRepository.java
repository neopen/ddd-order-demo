package com.hengline.ddd.domain.repository;

import com.hengline.ddd.domain.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

/**
 * <b>
 * <p>
 *
 * @Author HengLine
 * @Email haeng2030@gmail.com
 * @Date 2025/8/27 17:13
 */
@Component
public interface OrderRepository extends JpaRepository<Order, Long> {
}
