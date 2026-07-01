package dev.deepdive.actuator.content;

import java.util.List;

public record ContentSearchResult(String keyword, List<String> titles) {
}
