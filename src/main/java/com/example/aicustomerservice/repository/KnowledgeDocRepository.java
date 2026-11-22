package com.example.aicustomerservice.repository;

import com.example.aicustomerservice.entity.KnowledgeDoc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeDocRepository extends JpaRepository<KnowledgeDoc, Long> {

    List<KnowledgeDoc> findAllByOrderByIdAsc();
}