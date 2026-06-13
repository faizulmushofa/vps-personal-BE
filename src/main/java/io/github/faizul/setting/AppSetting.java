package io.github.faizul.setting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app_settings")
public class AppSetting {

    @Id
    private Long id;

    @Column("setting_key")
    private String key;

    @Column("setting_value")
    private String value;

    @Column("description")
    private String description;
}
