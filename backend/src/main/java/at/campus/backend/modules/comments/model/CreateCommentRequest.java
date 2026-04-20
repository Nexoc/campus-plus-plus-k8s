package at.campus.backend.modules.comments.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * CreateCommentRequest - Request model for creating a comment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateCommentRequest {

    private String content;

    // Constructors

    public CreateCommentRequest() {
    }

    public CreateCommentRequest(String content) {
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
