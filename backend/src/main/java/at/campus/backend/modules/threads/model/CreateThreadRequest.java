package at.campus.backend.modules.threads.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * CreateThreadRequest - Request model for creating a thread.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateThreadRequest {

    private UUID courseId;
    private String title;
    private String content;

    // Constructors

    public CreateThreadRequest() {
    }

    public CreateThreadRequest(UUID courseId, String title) {
        this.courseId = courseId;
        this.title = title;
    }

    // Getters and Setters

    public UUID getCourseId() {
        return courseId;
    }

    public void setCourseId(UUID courseId) {
        this.courseId = courseId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
