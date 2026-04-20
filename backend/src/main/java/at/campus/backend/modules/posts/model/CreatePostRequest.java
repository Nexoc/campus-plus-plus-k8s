package at.campus.backend.modules.posts.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * CreatePostRequest - Request model for creating a post.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreatePostRequest {

    private String content;

    // Constructors

    public CreatePostRequest() {
    }

    public CreatePostRequest(String content) {
        this.content = content;
    }

    // Getters and Setters

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
