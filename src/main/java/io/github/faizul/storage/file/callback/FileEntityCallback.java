package io.github.faizul.storage.file.callback;

import io.github.faizul.security.jwt.EncryptionService;
import io.github.faizul.storage.file.model.File;
import org.reactivestreams.Publisher;
import org.springframework.data.r2dbc.mapping.OutboundRow;
import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import org.springframework.data.r2dbc.mapping.event.BeforeSaveCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.r2dbc.core.Parameter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class FileEntityCallback implements BeforeSaveCallback<File>, AfterConvertCallback<File> {

    private final EncryptionService encryptionService;

    public FileEntityCallback(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    @Override
    public Publisher<File> onBeforeSave(File entity, OutboundRow row, SqlIdentifier table) {
        if (entity != null && "GOOGLE_DRIVE".equals(entity.getProvider()) && entity.getOriginalFileName() != null) {
            String encryptedName = encryptionService.encrypt(entity.getOriginalFileName());
            // Mutate the entity so that the returned entity reflects the saved state if needed
            entity.setOriginalFileName(encryptedName);
            SqlIdentifier targetKey = null;
            for (SqlIdentifier key : row.keySet()) {
                if ("original_file_name".equals(key.getReference())) {
                    targetKey = key;
                    break;
                }
            }
            if (targetKey != null) {
                row.put(targetKey, Parameter.from(encryptedName));
            } else {
                row.put(SqlIdentifier.quoted("original_file_name"), Parameter.from(encryptedName));
            }
        }
        return Mono.just(entity);
    }

    @Override
    public Publisher<File> onAfterConvert(File entity, SqlIdentifier table) {
        if (entity != null && "GOOGLE_DRIVE".equals(entity.getProvider()) && entity.getOriginalFileName() != null) {
            String decryptedName = encryptionService.decrypt(entity.getOriginalFileName());
            entity.setOriginalFileName(decryptedName);
        }
        return Mono.just(entity);
    }
}
