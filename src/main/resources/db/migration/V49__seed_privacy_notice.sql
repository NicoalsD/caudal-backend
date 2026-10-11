-- V49: first version of the privacy notice (docs/Seguridad.md, section 2.17).
-- The text is a draft for the project and still needs legal review: it does not name a data
-- controller or legal terms that the team has not defined. A new text is a new version, never an
-- edit of this one; the SHA-256 lets anyone check which text a person accepted.

INSERT INTO iam.privacy_notice_versions (version, text_es, text_sha256, effective_at)
SELECT 1, t.text_es, encode(digest(t.text_es, 'sha256'), 'hex'), now()
  FROM (VALUES (
    'Aviso de privacidad de CAUDAL (versión 1, borrador sujeto a revisión jurídica).' || E'\n\n' ||
    'CAUDAL es el cuaderno digital del acueducto. Para que puedas entrar guardamos tu nombre de '
      || 'usuario, tu nombre completo, tu rol en el acueducto y el registro de tus ingresos. La '
      || 'dirección IP se guarda solo como un código que no se puede revertir.' || E'\n\n' ||
    'Usamos estos datos únicamente para operar el acueducto: registrar lecturas, proponer y '
      || 'publicar turnos, y llevar las actas. La página pública, el mensaje de WhatsApp y el '
      || 'cartel nunca muestran nombres ni teléfonos.' || E'\n\n' ||
    'Puedes pedir a la Junta consultar, corregir o suprimir tus datos, conforme a la Ley 1581 de '
      || '2012. Los datos del acueducto de demostración son simulados.'
  )) AS t(text_es);
