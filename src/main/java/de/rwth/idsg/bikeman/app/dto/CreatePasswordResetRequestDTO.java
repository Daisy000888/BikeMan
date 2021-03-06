package de.rwth.idsg.bikeman.app.dto;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Email;

@Getter
@Setter
public class CreatePasswordResetRequestDTO {

    @Email
    private String login;
}
