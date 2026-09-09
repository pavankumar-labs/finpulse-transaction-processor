package com.finpulse.service.email;

public class EmailTemplates {

    public static String registrationConfirmation(String companyName, String companyCode) {
        String inner = """
                <p style="margin:0 0 4px; font-size:13px; color:#6b6b76; text-transform:uppercase; letter-spacing:0.5px;">Application Received</p>
                <h2 style="margin:0 0 16px; font-size:20px; color:#1a1a2e; font-family: Arial, Helvetica, sans-serif;">Thanks, %s!</h2>
                <p style="margin:0 0 20px; font-size:15px; color:#444450; line-height:1.5; font-family: Arial, Helvetica, sans-serif;">
                  We've received your application to connect with FinPulse. Our team will review it shortly, and you'll hear from us once a decision is made.
                </p>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f7f7fa; border-radius:6px;">
                  <tr>
                    <td style="padding:16px 20px; font-size:13px; color:#6b6b76; font-family: Arial, Helvetica, sans-serif;">
                      Company Code: <strong style="color:#1a1a2e;">%s</strong>
                    </td>
                  </tr>
                </table>
                """.formatted(companyName, companyCode);

        return shell(inner, "Your FinPulse application has been received.");
    }

    public static String approvalNotice(
            String companyName,
            String loginEmail,
            String companyCode,
            String rawApiKey,
            String rawOwnerPassword) {

        String inner = """
                <p style="margin:0 0 4px; font-size:13px; color:#6b6b76; text-transform:uppercase; letter-spacing:0.5px;">Application Approved</p>
                <h2 style="margin:0 0 16px; font-size:20px; color:#1a1a2e; font-family: Arial, Helvetica, sans-serif;">Welcome, %s!</h2>
                <p style="margin:0 0 20px; font-size:15px; color:#444450; line-height:1.5; font-family: Arial, Helvetica, sans-serif;">
                  Your application has been approved. Save the details below now — for security, they will not be shown again.
                </p>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="background-color:#f7f7fa; border-radius:6px; margin-bottom:16px;">
                  <tr>
                    <td style="padding:16px 20px;">

                      <div style="font-size:13px; color:#6b6b76; font-family: Arial, Helvetica, sans-serif; padding-bottom:6px;">
                        Company Code
                      </div>

                      <div style="font-size:14px; color:#1a1a2e; font-weight:bold; font-family: monospace; padding-bottom:16px;">
                        %s
                      </div>

                      <div style="font-size:13px; color:#6b6b76; font-family: Arial, Helvetica, sans-serif; padding-bottom:6px;">
                        API Key (for file uploads)
                      </div>

                      <div style="font-size:13px; color:#1a1a2e; font-weight:bold; font-family: monospace; word-break:break-all;">
                        %s
                      </div>

                    </td>
                  </tr>
                </table>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="background-color:#f7f7fa; border-radius:6px;">
                  <tr>
                    <td style="padding:16px 20px;">

                      <div style="font-size:13px; color:#6b6b76; font-family: Arial, Helvetica, sans-serif; padding-bottom:6px;">
                        Dashboard Login (username)
                      </div>

                      <div style="font-size:14px; color:#1a1a2e; font-weight:bold; font-family: monospace; padding-bottom:16px;">
                        %s
                      </div>

                      <div style="font-size:13px; color:#6b6b76; font-family: Arial, Helvetica, sans-serif; padding-bottom:6px;">
                        Temporary Password
                      </div>

                      <div style="font-size:14px; color:#1a1a2e; font-weight:bold; font-family: monospace;">
                        %s
                      </div>

                    </td>
                  </tr>
                </table>

                <p style="margin:20px 0 0; font-size:13px; color:#9a9aa5; font-family: Arial, Helvetica, sans-serif;">
                  Include the API key as the 'X-API-Key' header on every request to the FinPulse ingestion API.
                  You will be asked to set a new dashboard password on first login.
                </p>
                """.formatted(
                companyName,
                companyCode,
                rawApiKey,
                loginEmail,
                rawOwnerPassword
        );

        return shell(inner, "Your FinPulse application has been approved.");
    }


    public static String credentialIssued(String email, String rawPassword) {
        String inner = """
                <p style="margin:0 0 4px; font-size:13px; color:#6b6b76; text-transform:uppercase; letter-spacing:0.5px;">Account Created</p>

                <h2 style="margin:0 0 16px; font-size:20px; color:#1a1a2e; font-family: Arial, Helvetica, sans-serif;">
                  Your FinPulse access is ready
                </h2>

                <p style="margin:0 0 20px; font-size:15px; color:#444450; line-height:1.5; font-family: Arial, Helvetica, sans-serif;">
                  An account has been created for you on the FinPulse dashboard. Use the credentials below to log in.
                </p>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="background-color:#f7f7fa; border-radius:6px;">
                  <tr>
                    <td style="padding:16px 20px;">

                      <div style="font-size:13px; color:#6b6b76; font-family: Arial, Helvetica, sans-serif; padding-bottom:6px;">
                        Username
                      </div>

                      <div style="font-size:14px; color:#1a1a2e; font-weight:bold; font-family: monospace; padding-bottom:16px;">
                        %s
                      </div>

                      <div style="font-size:13px; color:#6b6b76; font-family: Arial, Helvetica, sans-serif; padding-bottom:6px;">
                        Temporary Password
                      </div>

                      <div style="font-size:14px; color:#1a1a2e; font-weight:bold; font-family: monospace;">
                        %s
                      </div>

                    </td>
                  </tr>
                </table>

                <p style="margin:20px 0 0; font-size:13px; color:#9a9aa5; font-family: Arial, Helvetica, sans-serif;">
                  You will be asked to set a new password the first time you log in.
                  This temporary password expires in 7 days.
                </p>
                """.formatted(email, rawPassword);

        return shell(inner, "Your FinPulse account is ready.");
    }


    public static String rejectionNotice(String companyName) {
        String inner = """
                <p style="margin:0 0 4px; font-size:13px; color:#6b6b76; text-transform:uppercase; letter-spacing:0.5px;">Application Update</p>

                <h2 style="margin:0 0 16px; font-size:20px; color:#1a1a2e; font-family: Arial, Helvetica, sans-serif;">
                  Hi %s,
                </h2>

                <p style="margin:0 0 20px; font-size:15px; color:#444450; line-height:1.5; font-family: Arial, Helvetica, sans-serif;">
                  After review, we're unable to approve your application to connect with FinPulse at this time.
                  If you'd like more information, please reach out to our support team.
                </p>
                """.formatted(companyName);

        return shell(inner, "An update on your FinPulse application.");
    }


    public static String passwordResetLink(String resetLink) {
        String inner = """
                <p style="margin:0 0 4px; font-size:13px; color:#6b6b76; text-transform:uppercase; letter-spacing:0.5px;">Password Reset</p>

                <h2 style="margin:0 0 16px; font-size:20px; color:#1a1a2e; font-family: Arial, Helvetica, sans-serif;">
                  Reset your password
                </h2>

                <p style="margin:0 0 20px; font-size:15px; color:#444450; line-height:1.5; font-family: Arial, Helvetica, sans-serif;">
                  We received a request to reset your FinPulse password.
                  This link expires in 1 hour and can only be used once.
                </p>

                <table role="presentation" cellpadding="0" cellspacing="0">
                  <tr>
                    <td style="background-color:#1a1a2e; border-radius:6px;">
                      <a href="%s"
                         style="display:inline-block; padding:12px 24px; color:#ffffff; font-size:14px; font-weight:bold; text-decoration:none; font-family: Arial, Helvetica, sans-serif;">
                        Reset Password
                      </a>
                    </td>
                  </tr>
                </table>

                <p style="margin:20px 0 0; font-size:13px; color:#9a9aa5; font-family: Arial, Helvetica, sans-serif;">
                  If you didn't request this, you can safely ignore this email — your password will not be changed.
                </p>
                """.formatted(resetLink);

        return shell(inner, "Reset your FinPulse password.");
    }


    private static String shell(String innerContent, String preheaderText) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>

                <body style="margin:0; padding:0;">

                  <div style="display:none; max-height:0; overflow:hidden; opacity:0;">
                    %s
                  </div>

                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                         style="background-color:#f4f4f7;">

                    <tr>
                      <td align="center" style="padding:24px 12px;">

                        <table role="presentation" width="480" cellpadding="0" cellspacing="0"
                               style="max-width:480px; width:100%%; background-color:#ffffff; border-radius:8px; overflow:hidden;">

                          <tr>
                            <td style="background-color:#1a1a2e; padding:24px; text-align:center;">
                              <span style="color:#ffffff; font-size:22px; font-weight:bold; font-family: Arial, Helvetica, sans-serif; letter-spacing:0.5px;">
                                FinPulse
                              </span>
                            </td>
                          </tr>

                          <tr>
                            <td style="padding:32px 28px;">
                              %s
                            </td>
                          </tr>

                          <tr>
                            <td style="background-color:#f0f0f3; padding:16px 28px; text-align:center;">
                              <span style="font-size:12px; color:#9a9aa5; font-family: Arial, Helvetica, sans-serif;">
                                This is an automated message from FinPulse — please do not reply.
                              </span>
                            </td>
                          </tr>

                        </table>

                      </td>
                    </tr>

                  </table>

                </body>
                </html>
                """.formatted(preheaderText, innerContent);
    }
}