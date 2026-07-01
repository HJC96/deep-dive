package dev.deepdive.actuator.content;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContentSearchController {

    private final ContentSearchService contentSearchService;

    public ContentSearchController(ContentSearchService contentSearchService) {
        this.contentSearchService = contentSearchService;
    }

    @GetMapping("/contents/search")
    public ContentSearchResult search(@RequestParam(defaultValue = "actuator") String keyword) {
        return contentSearchService.search(keyword);
    }
}
