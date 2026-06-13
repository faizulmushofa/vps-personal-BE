package io.github.faizul.notification;

public class EmailTemplateFactory {

    public static String getOtpTemplate(String title, String subtitle, String otpCode) {
        return String.format(
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "  <meta charset=\"utf-8\">\n" +
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "  <title>%s</title>\n" +
            "</head>\n" +
            "<body style=\"font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #f8fafc; padding: 40px 20px; color: #334155; margin: 0;\">\n" +
            "  <div style=\"max-width: 500px; margin: 0 auto; background-color: #ffffff; border-radius: 24px; padding: 40px; box-shadow: 0 10px 25px -5px rgba(15, 23, 42, 0.04), 0 8px 10px -6px rgba(15, 23, 42, 0.04); border: 1px solid #f1f5f9;\">\n" +
            "    <div style=\"text-align: center; margin-bottom: 30px;\">\n" +
            "      <span style=\"font-size: 24px; font-weight: 800; color: #0052cc; letter-spacing: -0.03em;\">Horizon Drive</span>\n" +
            "      <div style=\"font-size: 11px; font-weight: 700; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.1em; margin-top: 4px;\">Premium Cloud Storage</div>\n" +
            "    </div>\n" +
            "    <h2 style=\"font-size: 18px; font-weight: 700; color: #0f172a; margin-top: 0; margin-bottom: 12px; text-align: center;\">%s</h2>\n" +
            "    <p style=\"font-size: 13px; color: #64748b; line-height: 1.6; text-align: center; margin-top: 0; margin-bottom: 24px;\">%s</p>\n" +
            "    <div style=\"background-color: #f1f5f9; border-radius: 16px; padding: 20px; text-align: center; margin-bottom: 24px; border: 1px dashed #cbd5e1;\">\n" +
            "      <span style=\"font-size: 32px; font-weight: 800; letter-spacing: 0.25em; color: #0052cc; font-family: monospace; padding-left: 0.25em;\">%s</span>\n" +
            "    </div>\n" +
            "    <p style=\"font-size: 11px; color: #94a3b8; text-align: center; margin-top: 0; margin-bottom: 30px; line-height: 1.5;\">\n" +
            "      Kode OTP ini hanya berlaku selama <strong>5 menit</strong>. Jangan bagikan kode ini kepada siapa pun demi keamanan akun Anda.\n" +
            "    </p>\n" +
            "    <div style=\"height: 1px; background-color: #f1f5f9; margin-bottom: 24px;\"></div>\n" +
            "    <div style=\"text-align: center; font-size: 11px; color: #94a3b8; line-height: 1.5;\">\n" +
            "      <p style=\"margin: 0 0 4px 0;\">Jika Anda tidak meminta kode ini, silakan abaikan email ini.</p>\n" +
            "      <p style=\"margin: 0; font-weight: 600; color: #64748b;\">© 2026 Horizon Cloud. All rights reserved.</p>\n" +
            "    </div>\n" +
            "  </div>\n" +
            "</body>\n" +
            "</html>",
            title, title, subtitle, otpCode
        );
    }

    public static String getBugReportTemplate(String sender, String description) {
        return String.format(
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "  <meta charset=\"utf-8\">\n" +
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "  <title>Laporan Bug - Horizon Drive</title>\n" +
            "</head>\n" +
            "<body style=\"font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #f8fafc; padding: 40px 20px; color: #334155; margin: 0;\">\n" +
            "  <div style=\"max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 24px; padding: 40px; box-shadow: 0 10px 25px -5px rgba(15, 23, 42, 0.04), 0 8px 10px -6px rgba(15, 23, 42, 0.04); border: 1px solid #f1f5f9;\">\n" +
            "    <div style=\"text-align: center; margin-bottom: 30px;\">\n" +
            "      <span style=\"font-size: 24px; font-weight: 800; color: #0052cc; letter-spacing: -0.03em;\">Horizon Drive</span>\n" +
            "      <div style=\"font-size: 11px; font-weight: 700; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.1em; margin-top: 4px;\">Developer Feedback Center</div>\n" +
            "    </div>\n" +
            "    <div style=\"text-align: center; margin-bottom: 24px;\">\n" +
            "      <span style=\"background-color: #fee2e2; color: #ef4444; border-radius: 9999px; padding: 6px 16px; font-size: 10px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.05em;\">Laporan Kendala / Bug</span>\n" +
            "    </div>\n" +
            "    <h3 style=\"font-size: 16px; font-weight: 700; color: #0f172a; margin-top: 0; margin-bottom: 20px; text-align: center;\">Informasi Laporan Masalah Baru</h3>\n" +
            "    <div style=\"background-color: #f8fafc; border-radius: 16px; padding: 24px; margin-bottom: 24px; border: 1px solid #e2e8f0; text-align: left;\">\n" +
            "      <div style=\"margin-bottom: 20px;\">\n" +
            "        <span style=\"font-size: 10px; font-weight: 800; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.05em; display: block; margin-bottom: 4px;\">Pengirim</span>\n" +
            "        <span style=\"font-size: 14px; font-weight: 700; color: #334155;\">%s</span>\n" +
            "      </div>\n" +
            "      <div>\n" +
            "        <span style=\"font-size: 10px; font-weight: 800; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.05em; display: block; margin-bottom: 4px;\">Deskripsi Kendala</span>\n" +
            "        <div style=\"font-size: 13px; color: #475569; line-height: 1.6; white-space: pre-wrap; background-color: #ffffff; border-radius: 12px; padding: 16px; border: 1px solid #e2e8f0; font-family: sans-serif;\">%s</div>\n" +
            "      </div>\n" +
            "    </div>\n" +
            "    <div style=\"height: 1px; background-color: #f1f5f9; margin-bottom: 24px;\"></div>\n" +
            "    <div style=\"text-align: center; font-size: 11px; color: #94a3b8; line-height: 1.5;\">\n" +
            "      <p style=\"margin: 0 0 4px 0;\">Email ini dihasilkan secara otomatis oleh sistem Horizon Drive.</p>\n" +
            "      <p style=\"margin: 0; font-weight: 600; color: #64748b;\">© 2026 Horizon Cloud. All rights reserved.</p>\n" +
            "    </div>\n" +
            "  </div>\n" +
            "</body>\n" +
            "</html>",
            sender, description
        );
    }
}
