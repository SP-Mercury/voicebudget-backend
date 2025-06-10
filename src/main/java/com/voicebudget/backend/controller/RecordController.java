package com.voicebudget.backend.controller;

import com.voicebudget.backend.model.Record;
import com.voicebudget.backend.repository.RecordRepository;
import com.voicebudget.backend.service.SpeechToTextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class RecordController {

    private static final Logger logger = LoggerFactory.getLogger(RecordController.class);

    @Autowired
    private RecordRepository repository;

    @Autowired
    private SpeechToTextService speechService;

    // ✅ 語音上傳，並分類與記錄
    @PostMapping("/upload")
    public Record upload(@RequestParam("file") MultipartFile file) throws Exception {
        try {
            // 儲存語音檔案
            File temp = File.createTempFile("voice", ".webm");
            file.transferTo(temp);
            Map<String, Object> result = speechService.transcribe(temp);

            // 創建並儲存記錄
            Record record = new Record();
            record.setDescription((String) result.get("description"));
            record.setCategory((String) result.get("category"));
            record.setAmount((int) result.get("amount"));
            record.setType((String) result.getOrDefault("type", "支出"));
            record.setTime(LocalDateTime.now());

            logger.info("語音記錄成功: " + record); // 日誌輸出

            return repository.save(record);
        } catch (Exception e) {
            logger.error("語音記錄失敗：" + e.getMessage(), e);
            throw new RuntimeException("❌ 語音記錄失敗：" + e.getMessage(), e);
        }
    }

    // ✅ 查詢 + 統計
    @GetMapping("/records/summary")
    public Map<String, Object> summary(@RequestParam(required = false) Integer year,
                                       @RequestParam(required = false) Integer month,
                                       @RequestParam(required = false) Integer day) {
        List<Record> all = repository.findAll();
        List<Record> filtered = new ArrayList<>();

        for (Record r : all) {
            LocalDateTime time = r.getTime();
            boolean match = true;
            if (year != null && time.getYear() != year) match = false;
            if (month != null && time.getMonthValue() != month) match = false;
            if (day != null && time.getDayOfMonth() != day) match = false;
            if (match) filtered.add(r);
        }

        int income = filtered.stream().filter(r -> "收入".equals(r.getType())).mapToInt(Record::getAmount).sum();
        int expense = filtered.stream().filter(r -> "支出".equals(r.getType())).mapToInt(Record::getAmount).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("records", filtered);
        result.put("income", income);
        result.put("expense", expense);
        result.put("total", income - expense);
        return result;
    }

    // ✅ 修改紀錄（支援前端 .put(...)）
    @PutMapping("/records/{id}")
    public Record update(@PathVariable Long id, @RequestBody Record update) {
        try {
            return repository.findById(id).map(r -> {
                r.setDescription(update.getDescription());
                r.setCategory(update.getCategory());
                r.setAmount(update.getAmount());
                r.setType(update.getType());
                r.setTime(update.getTime());
                return repository.save(r);
            }).orElseThrow(() -> new RuntimeException("找不到記錄 id = " + id));
        } catch (Exception e) {
            logger.error("修改記錄失敗：" + e.getMessage(), e);
            throw new RuntimeException("❌ 修改記錄失敗：" + e.getMessage(), e);
        }
    }

    // ✅ 刪除紀錄
    @DeleteMapping("/records/{id}")
    public void delete(@PathVariable Long id) {
        try {
            repository.deleteById(id);
        } catch (Exception e) {
            logger.error("刪除記錄失敗：" + e.getMessage(), e);
            throw new RuntimeException("❌ 刪除記錄失敗：" + e.getMessage(), e);
        }
    }

    // ✅ 手動新增記帳（新增手動記帳API）
    @PostMapping("/records")
    public Record createRecord(@RequestBody Record record) {
        try {
            // 設定時間為當前時間
            record.setTime(LocalDateTime.now());

            logger.info("手動記錄: " + record); // 日誌輸出手動記錄

            // 儲存記錄到資料庫
            return repository.save(record);
        } catch (Exception e) {
            logger.error("添加手動記錄失敗：" + e.getMessage(), e);
            throw new RuntimeException("❌ 添加手動記錄失敗：" + e.getMessage(), e);
        }
    }
}
