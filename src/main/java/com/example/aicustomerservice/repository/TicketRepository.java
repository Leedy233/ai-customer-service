package com.example.aicustomerservice.repository;

import com.example.aicustomerservice.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByUserIdOrderByIdDesc(Long userId);

    Optional<Ticket> findByTicketNo(String ticketNo);

    Optional<Ticket> findTopByTicketNoStartingWithOrderByTicketNoDesc(String prefix);
}