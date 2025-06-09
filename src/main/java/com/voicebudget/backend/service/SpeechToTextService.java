package com.voicebudget.backend.service;

import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.regex.*;

@Service
public class SpeechToTextService {

    @Value("${OPENROUTER_API_KEY:}")
    private String openrouterApiKey;

    public Map<String, Object> transcribe(File file) throws Exception {
        System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", "/etc/secrets/speech-key.json");

        ByteString audioBytes = ByteString.readFrom(new FileInputStream(file));

        RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.WEBM_OPUS)
                .setSampleRateHertz(48000)
                .setLanguageCode("zh-TW")
                .build();

        RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(audioBytes)
                .build();

        String transcript;
        try (SpeechClient speechClient = SpeechClient.create()) {
            RecognizeResponse response = speechClient.recognize(config, audio);
            transcript = response.getResultsList().stream()
                    .flatMap(r -> r.getAlternativesList().stream())
                    .map(SpeechRecognitionAlternative::getTranscript)
                    .reduce("", String::concat);
        }

        String prompt = String.format(
            "你是一位財務記帳助手，請從下列中文語音內容中分析出：\n" +
            "- category：消費分類，必須為「飲食」「交通」「娛樂」「購物」「其他」五選一\n" +
            "- amount：消費金額，為新台幣整數（請不要加上單位）\n" +
            "- type：收入 或 支出\n\n" +
            "請僅輸出以下格式的 JSON（不得多出任何說明或註解）：\n" +
            "{\"category\": \"分類名稱\", \"amount\": 消費金額整數, \"type\": \"收入或支出\"}\n\n" +
            "語音內容如下：\"%s\"", transcript
        );

        String aiReply = callOpenRouter(prompt);
        String jsonText = extractJsonFromText(aiReply);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> result = mapper.readValue(jsonText, Map.class);
        result.put("description", transcript);
        return result;
    }

    private String callOpenRouter(String prompt) throws Exception {
        if (openrouterApiKey == null || openrouterApiKey.isBlank()) {
            throw new IllegalStateException("❌ OPENROUTER_API_KEY 環境變數未設定或為空");
        }

        String apiUrl = "https://openrouter.ai/api/v1/chat/completions";

        String body = """
        {
          "model": "deepseek/deepseek-chat-v3-0324:free",
          "messages": [
            {
              "role": "user",
              "content": "%s"
            }
          ]
        }
        """.formatted(prompt.replace("\"", "\\\""));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + openrouterApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("❌ OpenRouter 回應錯誤：" + response.statusCode() + " - " + response.body());
        }

        ObjectMapper mapper = new ObjectMapper();
        Map<?, ?> json = mapper.readValue(response.body(), Map.class);
        Map<?, ?> choice = (Map<?, ?>) ((List<?>) json.get("choices")).get(0);
        Map<?, ?> message = (Map<?, ?>) choice.get("message");
        return (String) message.get("content");
    }

    private String extractJsonFromText(String text) {
        Pattern pattern = Pattern.compile("\\{[^}]+\\}");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) return matcher.group();
        throw new RuntimeException("❌ AI 回傳格式錯誤：" + text);
    }
}
