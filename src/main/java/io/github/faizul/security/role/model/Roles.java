package io.github.faizul.security.role.model;

import lombok.Getter;

@Getter
public enum Roles {
    ADMIN("admin"),
    USER("user");

    private String role;
    Roles(String name){
        this.role = name;
    }


}
