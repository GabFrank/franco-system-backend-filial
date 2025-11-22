package com.franco.dev.fmc.model;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PushNotificationRequest {
    @NotBlank
    @Size(max = 120)
    private String title;
    @NotBlank
    @Size(max = 750)
    private String message;
    @Size(max = 50)
    private String type;
    private String topic;
    private String token;
    private List<String> tokens;
    private List<Long> usuarioIds;
    @Size(max = 500)
    private String data;

    public boolean hasDirectTokens() {
        return (token != null && !token.isEmpty()) || (tokens != null && !tokens.isEmpty());
    }

    public boolean hasTopic() {
        return topic != null && !topic.isEmpty();
    }

    public boolean hasUsuarios() {
        return usuarioIds != null && !usuarioIds.isEmpty();
    }
}
