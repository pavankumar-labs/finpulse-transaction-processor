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

    public static String approvalNotice(String companyName, String companyCode, String rawApiKey) {
        String inner = """
                <p style="margin:0 0 4px; font-size:13px; color:#6b6b76; text-transform:uppercase; letter-spacing:0.5px;">Application Approved</p>
                <h2 style="margin:0 0 16px; font-size:20px; color:#1a1a2e; font-family: Arial, Helvetica, sans-serif;">Welcome, %s!</h2>
                <p style="margin:0 0 20px; font-size:15px; color:#444450; line-height:1.5; font-family: Arial, Helvetica, sans-serif;">
                  Your application has been approved. Save the API key below now — for security, it will not be shown again.
                </p>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f7f7fa; border-radius:6px;">
                  <tr>
                    <td style="padding:16px 20px;">
                      <div style="font-size:13px; color:#6b6b76; font-family: Arial, Helvetica, sans-serif; padding-bottom:6px;">Company Code</div>
                      <div style="font-size:14px; color:#1a1a2e; font-weight:bold; font-family: monospace; padding-bottom:16px;">%s</div>
                      <div style="font-size:13px; color:#6b6b76; font-family: Arial, Helvetica, sans-serif; padding-bottom:6px;">API Key</div>
                      <div style="font-size:13px; color:#1a1a2e; font-weight:bold; font-family: monospace; word-break:break-all;">%s</div>
                    </td>
                  </tr>
                </table>
                <p style="margin:20px 0 0; font-size:13px; color:#9a9aa5; font-family: Arial, Helvetica, sans-serif;">
                  Include this key as the 'X-API-Key' header on every request to the FinPulse ingestion API.
                </p>
                """.formatted(companyName, companyCode, rawApiKey);
        return shell(inner, "Your FinPulse application has been approved.");
    }

    public static String rejectionNotice(String companyName) {
        String inner = """
                <p style="margin:0 0 4px; font-size:13px; color:#6b6b76; text-transform:uppercase; letter-spacing:0.5px;">Application Update</p>
                <h2 style="margin:0 0 16px; font-size:20px; color:#1a1a2e; font-family: Arial, Helvetica, sans-serif;">Hi %s,</h2>
                <p style="margin:0 0 20px; font-size:15px; color:#444450; line-height:1.5; font-family: Arial, Helvetica, sans-serif;">
                  After review, we're unable to approve your application to connect with FinPulse at this time. If you'd like more information, please reach out to our support team.
                </p>
                """.formatted(companyName);
        return shell(inner, "An update on your FinPulse application.");
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
                  <div style="display:none; max-height:0; overflow:hidden; opacity:0;">%s</div>
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f4f7;">
                    <tr>
                      <td align="center" style="padding:24px 12px;">
                        <table role="presentation" width="480" cellpadding="0" cellspacing="0" style="max-width:480px; width:100%%; background-color:#ffffff; border-radius:8px; overflow:hidden;">
                          <tr>
                            <td style="background-color:#1a1a2e; padding:24px; text-align:center;">
                              <span style="color:#ffffff; font-size:22px; font-weight:bold; font-family: Arial, Helvetica, sans-serif; letter-spacing:0.5px;">FinPulse</span>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:32px 28px;">
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="background-color:#f0f0f3; padding:16px 28px; text-align:center;">
                              <span style="font-size:12px; color:#9a9aa5; font-family: Arial, Helvetica, sans-serif;">This is an automated message from FinPulse — please do not reply.</span>
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
