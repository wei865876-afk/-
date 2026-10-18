package com.school.student_manage.ai.web;

/** 前端发起对话的请求体 */
public class AiChatRequest {

    private Integer conversationId;
    private String message;
    private String scene;
    private Boolean newConversation;

    public Integer getConversationId() {
        return conversationId;
    }

    public void setConversationId(Integer conversationId) {
        this.conversationId = conversationId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public Boolean getNewConversation() {
        return newConversation;
    }

    public void setNewConversation(Boolean newConversation) {
        this.newConversation = newConversation;
    }
}
