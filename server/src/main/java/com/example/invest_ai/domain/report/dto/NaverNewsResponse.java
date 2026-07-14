package com.example.invest_ai.domain.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverNewsResponse(
        @JsonProperty("lastBuildDate") String lastBuildDate,
        @JsonProperty("total") int total,
        @JsonProperty("start") int start,
        @JsonProperty("display") int display,
        @JsonProperty("items") List<Item> items
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JsonProperty("title") String title,
            @JsonProperty("originallink") String originallink,
            @JsonProperty("link") String link,
            @JsonProperty("description") String description,
            @JsonProperty("pubDate") String pubDate
    ) {
        public String getStrippedTitle() {
            return stripHtml(title);
        }

        public String getStrippedDescription() {
            return stripHtml(description);
        }

        private String stripHtml(String html) {
            if (html == null) {
                return "";
            }
            String text = html.replaceAll("<[^>]*>", "");
            text = text.replace("\u0026lt;", "<");
            text = text.replace("\u0026gt;", ">");
            text = text.replace("\u0026quot;", "\"");
            text = text.replace("\u0026amp;", "\u0026");
            text = text.replace("\u0026apos;", "'");
            return text.trim();
        }
    }
}