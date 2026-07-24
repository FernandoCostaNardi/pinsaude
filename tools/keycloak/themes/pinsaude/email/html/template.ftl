<#--
  Layout compartilhado por todos os e-mails nativos do Keycloak (executeActions.ftl,
  password-reset.ftl, email-verification.ftl etc.) — cada um só chama
  <@layout.emailLayout>${...}</@layout.emailLayout>, então sobrescrever só este arquivo já
  aplica o layout da Pin Saúde a todos eles sem precisar duplicar cada template de conteúdo.
  Mesmo estilo visual dos e-mails Thymeleaf do onboarding (ver templates/email/*.html):
  header azul com o nome da marca, card branco centralizado, rodapé cinza claro.
-->
<#macro emailLayout>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
</head>
<body style="margin:0;padding:0;background:#f4f7fb;font-family:Arial,Helvetica,sans-serif;">
<table width="100%" cellpadding="0" cellspacing="0" style="background:#f4f7fb;padding:32px 16px;">
  <tr><td align="center">
    <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,0.08);">

      <!-- Header -->
      <tr>
        <td style="background:#02A9F7;padding:32px 40px;text-align:center;">
          <p style="margin:0;font-size:26px;font-weight:900;color:#ffffff;letter-spacing:-0.5px;">Pin Saúde</p>
          <p style="margin:6px 0 0;font-size:12px;color:#BAE9FF;letter-spacing:2px;text-transform:uppercase;">Sistema de Gestão Médica</p>
        </td>
      </tr>

      <!-- Body -->
      <tr>
        <td style="padding:40px;font-size:15px;color:#4a4a6a;line-height:1.6;">
          <#nested>
        </td>
      </tr>

      <!-- Footer -->
      <tr>
        <td style="background:#f8faff;border-top:1px solid #e8edf5;padding:24px 40px;text-align:center;">
          <p style="margin:0;font-size:12px;color:#aaa;">Pin Saúde — Gestão Fiscal para Médicos</p>
          <p style="margin:4px 0 0;font-size:11px;color:#ccc;">noreply@pinsaude.com.br</p>
        </td>
      </tr>

    </table>
  </td></tr>
</table>
</body>
</html>
</#macro>
