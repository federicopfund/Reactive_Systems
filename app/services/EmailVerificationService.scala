package services

import javax.inject.{Inject, Singleton}
import models.EmailVerificationCode
import repositories.EmailVerificationRepository
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
import java.time.Instant
import java.time.temporal.ChronoUnit

@Singleton
class EmailVerificationService @Inject()(
  verificationRepository: EmailVerificationRepository,
  emailService: EmailService
)(implicit ec: ExecutionContext) {

  private val CODE_LENGTH = 3
  private val CODE_EXPIRATION_MINUTES = 5

  /**
   * Genera un código de verificación aleatorio de 3 dígitos
   */
  private def generateCode(): String = {
    val random = new Random()
    (100 + random.nextInt(900)).toString
  }

  /**
   * Crea y envía un código de verificación por email
   */
  def createAndSendCode(userId: Long, email: String): Future[EmailVerificationCode] = {
    val code = generateCode()
    val expiresAt = Instant.now().plus(CODE_EXPIRATION_MINUTES, ChronoUnit.MINUTES)
    
    val verificationCode = EmailVerificationCode(
      userId = userId,
      email = email,
      code = code,
      expiresAt = expiresAt
    )

    for {
      created <- verificationRepository.create(verificationCode)
      _ <- emailService.sendVerificationCode(email, code, CODE_EXPIRATION_MINUTES)
    } yield created
  }

  /**
   * Verifica un código ingresado por el usuario
   */
  def verifyCode(userId: Long, inputCode: String): Future[Either[String, Boolean]] = {
    verificationRepository.findLatestByUserId(userId).flatMap {
      case None =>
        Future.successful(Left("No se encontró un código de verificación"))
      
      case Some(verificationCode) if verificationCode.verified =>
        Future.successful(Left("Este código ya ha sido utilizado"))
      
      case Some(verificationCode) if verificationCode.isExpired =>
        Future.successful(Left("El código ha expirado. Solicita uno nuevo"))
      
      case Some(verificationCode) if !verificationCode.canAttempt =>
        Future.successful(Left("Demasiados intentos fallidos. Solicita un nuevo código"))
      
      case Some(verificationCode) if verificationCode.code != inputCode =>
        verificationRepository.incrementAttempts(verificationCode.id.get).map { _ =>
          Left("Código incorrecto. Intenta nuevamente")
        }
      
      case Some(verificationCode) =>
        verificationRepository.verify(verificationCode.id.get).map { _ =>
          Right(true)
        }
    }
  }

  /**
   * Limpia códigos expirados
   */
  def cleanupExpiredCodes(): Future[Int] = {
    verificationRepository.deleteExpired()
  }
}
