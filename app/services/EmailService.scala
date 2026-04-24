package services

import javax.inject.{Inject, Singleton}
import javax.mail._
import javax.mail.internet._
import java.util.Properties
import play.api.{Configuration, Logger}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Success, Failure}

@Singleton
class EmailService @Inject()(
  config: Configuration
)(implicit ec: ExecutionContext) {

  private val logger = Logger(this.getClass)
  
  private val emailEnabled = config.getOptional[Boolean]("email.enabled").getOrElse(false)
  
  // Configuración SMTP
  private val smtpHost = config.getOptional[String]("email.smtp.host").getOrElse("smtp.gmail.com")
  private val smtpPort = config.getOptional[Int]("email.smtp.port").getOrElse(587)
  private val smtpUser = config.getOptional[String]("email.smtp.user").getOrElse("")
  private val smtpPassword = config.getOptional[String]("email.smtp.password").getOrElse("")
  private val fromEmail = config.getOptional[String]("email.from").getOrElse("noreply@reactivemanifesto.com")
  private val fromName = config.getOptional[String]("email.fromName").getOrElse("Reactive Manifesto")

  /**
   * Crea una sesión SMTP
   */
  private def createSession(): Session = {
    val props = new Properties()
    props.put("mail.smtp.auth", "true")
    props.put("mail.smtp.starttls.enable", "true")
    props.put("mail.smtp.host", smtpHost)
    props.put("mail.smtp.port", smtpPort.toString)
    props.put("mail.smtp.ssl.trust", smtpHost)
    
    Session.getInstance(props, new Authenticator() {
      override protected def getPasswordAuthentication(): PasswordAuthentication = {
        new PasswordAuthentication(smtpUser, smtpPassword)
      }
    })
  }

  /**
   * Envía un email usando JavaMail
   */
  private def sendEmail(to: String, subject: String, htmlBody: String): Try[Unit] = Try {
    val session = createSession()
    val message = new MimeMessage(session)
    
    message.setFrom(new InternetAddress(fromEmail, fromName))
    message.setRecipients(Message.RecipientType.TO, to)
    message.setSubject(subject)
    message.setContent(htmlBody, "text/html; charset=utf-8")
    
    Transport.send(message)
    logger.info(s"✅ Email enviado exitosamente a $to")
  }

  /**
   * Envía un código de verificación por email
   */
  def sendVerificationCode(email: String, code: String, expirationMinutes: Int): Future[Boolean] = Future {
    if (emailEnabled) {
      val subject = "Código de Verificación - Reactive Manifesto"
      val htmlBody = createVerificationEmailHtml(code, expirationMinutes)
      
      sendEmail(email, subject, htmlBody) match {
        case Success(_) => 
          logger.info(s"📧 Código $code enviado a $email")
          true
        case Failure(ex) =>
          logger.error(s"❌ Error enviando email a $email: ${ex.getMessage}", ex)
          false
      }
    } else {
      // Modo desarrollo: solo log
      logger.info(s"""
        |========================================
        | 📧 CÓDIGO DE VERIFICACIÓN (DEV MODE)
        |========================================
        | Email: $email
        | Código: $code
        | Expira en: $expirationMinutes minutos
        |========================================
      """.stripMargin)
      true
    }
  }

  /**
   * Envía email de bienvenida
   */
  def sendWelcomeEmail(email: String, fullName: String): Future[Boolean] = Future {
    if (emailEnabled) {
      val subject = "¡Bienvenido a Reactive Manifesto!"
      val htmlBody = createWelcomeEmailHtml(fullName)
      
      sendEmail(email, subject, htmlBody) match {
        case Success(_) => 
          logger.info(s"📧 Email de bienvenida enviado a $email")
          true
        case Failure(ex) =>
          logger.error(s"❌ Error enviando email de bienvenida a $email: ${ex.getMessage}", ex)
          false
      }
    } else {
      logger.info(s"[DEV] Email de bienvenida para $fullName ($email)")
      true
    }
  }

  /**
   * Envía un email de notificación genérica
   */
  def sendNotificationEmail(email: String, title: String, message: String): Future[Boolean] = Future {
    if (emailEnabled) {
      val htmlBody = s"""
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8">
          <style>
            body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px; }
            .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; padding: 40px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
            .header { text-align: center; margin-bottom: 30px; }
            .header h1 { color: #667eea; margin: 0; font-size: 1.25rem; }
            .content { color: #333; font-size: 15px; line-height: 1.6; }
            .footer { margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; text-align: center; color: #999; font-size: 12px; }
          </style>
        </head>
        <body>
          <div class="container">
            <div class="header"><h1>$$title</h1></div>
            <div class="content"><p>$$message</p></div>
            <div class="footer">
              <p>© $${java.time.Year.now().getValue()} Reactive Manifesto</p>
            </div>
          </div>
        </body>
        </html>
      """
      sendEmail(email, title, htmlBody) match {
        case Success(_) =>
          logger.info(s"📧 Notification email sent to $$email")
          true
        case Failure(ex) =>
          logger.error(s"❌ Error sending notification email to $$email: $${ex.getMessage}", ex)
          false
      }
    } else {
      logger.info(s"[DEV] Notification email: $$title → $$email")
      true
    }
  }

  /**
   * Crea el HTML del email de verificación
   */
  private def createVerificationEmailHtml(code: String, expirationMinutes: Int): String = {
    s"""
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <style>
          body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px; }
          .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; padding: 40px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
          .header { text-align: center; margin-bottom: 30px; }
          .header h1 { color: #667eea; margin: 0; }
          .code-box { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; text-align: center; border-radius: 10px; margin: 20px 0; }
          .code { font-size: 48px; font-weight: bold; letter-spacing: 10px; font-family: 'Courier New', monospace; }
          .info { color: #666; font-size: 14px; line-height: 1.6; }
          .footer { margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; text-align: center; color: #999; font-size: 12px; }
        </style>
      </head>
      <body>
        <div class="container">
          <div class="header">
            <h1>🔐 Código de Verificación</h1>
          </div>
          
          <p>Hola,</p>
          <p>Usa el siguiente código para verificar tu cuenta en Reactive Manifesto:</p>
          
          <div class="code-box">
            <div class="code">$code</div>
          </div>
          
          <div class="info">
            <p>⏱️ <strong>Este código expira en $expirationMinutes minutos</strong></p>
            <p>Si no solicitaste este código, puedes ignorar este email.</p>
            <p>Por seguridad, tienes máximo 3 intentos para ingresar el código correcto.</p>
          </div>
          
          <div class="footer">
            <p>© ${java.time.Year.now().getValue()} Reactive Manifesto. Todos los derechos reservados.</p>
          </div>
        </div>
      </body>
      </html>
    """
  }

  /**
   * Crea el HTML del email de bienvenida
   */
  private def createWelcomeEmailHtml(fullName: String): String = {
    s"""
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <style>
          body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px; }
          .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; padding: 40px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
          .header { text-align: center; margin-bottom: 30px; }
          .header h1 { color: #667eea; margin: 0; }
          .welcome-box { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; text-align: center; border-radius: 10px; margin: 20px 0; }
          .content { color: #333; font-size: 16px; line-height: 1.6; }
          .footer { margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; text-align: center; color: #999; font-size: 12px; }
        </style>
      </head>
      <body>
        <div class="container">
          <div class="header">
            <h1>¡Bienvenido a Reactive Manifesto!</h1>
          </div>
          
          <div class="welcome-box">
            <h2>👋 Hola, $fullName</h2>
            <p>Tu cuenta ha sido verificada exitosamente</p>
          </div>
          
          <div class="content">
            <p>Ahora puedes acceder a todo el contenido exclusivo de Reactive Manifesto:</p>
            <ul>
              <li>📚 Artículos sobre programación reactiva</li>
              <li>💼 Proyectos del portafolio</li>
              <li>📊 Demos interactivas</li>
              <li>📄 Documentación técnica</li>
            </ul>
            <p>¡Esperamos que disfrutes explorando el mundo de la programación reactiva!</p>
          </div>
          
          <div class="footer">
            <p>© ${java.time.Year.now().getValue()} Reactive Manifesto. Todos los derechos reservados.</p>
          </div>
        </div>
      </body>
      </html>
    """
  }
}
