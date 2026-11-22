package com.example.aicustomerservice.repository;

import com.example.aicustomerservice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    /** 加法：本人订单列表 */
    List<Order> findByUserIdOrderByCreateTimeDescIdDesc(Long userId);
}