package com.voicebudget.backend.repository;

import com.voicebudget.backend.model.Record;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordRepository extends JpaRepository<Record, Long> {
}
