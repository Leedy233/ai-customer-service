package com.example.aicustomerservice.repository;

import com.example.aicustomerservice.entity.RefundOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface RefundOrderRepository extends JpaRepository<RefundOrder, Long> {

    boolean existsByOrderNoAndStatusIn(String orderNo, Collection<String> statuses);
}