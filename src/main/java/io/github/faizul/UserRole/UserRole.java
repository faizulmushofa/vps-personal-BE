package io.github.faizul.UserRole;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_roles")
public class UserRole {

    @Column("user_id")
    private Long userId;

    @Column("role_id")
    private Long roleId;
}
