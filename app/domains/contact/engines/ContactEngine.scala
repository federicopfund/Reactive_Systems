package domains.contact.engines

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import domains.contact.repositories.ContactRepository
import domains.contact.models.ContactRecord
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

// Contact domain model
case class Contact(name: String, email: String, message: String)

// Commands for the reactive actor
sealed trait ContactCommand
case class SubmitContact(contact: Contact, replyTo: ActorRef[ContactResponse]) extends ContactCommand
private case class ContactSaved(savedContact: ContactRecord, replyTo: ActorRef[ContactResponse]) extends ContactCommand
private case class ContactSaveFailed(exception: Throwable, replyTo: ActorRef[ContactResponse]) extends ContactCommand

// Responses
sealed trait ContactResponse
case class ContactSubmitted(id: String) extends ContactResponse
case class ContactError(message: String) extends ContactResponse

// The reactive contact engine using Akka Typed with persistence
object ContactEngine {
  def apply(repository: ContactRepository)(implicit ec: ExecutionContext): Behavior[ContactCommand] = 
    active(repository)

  private def active(repository: ContactRepository)(implicit ec: ExecutionContext): Behavior[ContactCommand] = {
    Behaviors.receive { (context, message) =>
      message match {
        case SubmitContact(contact, replyTo) =>
          context.log.info(s"Processing contact from ${contact.name} (${contact.email})")
          
          // Crear registro para la base de datos
          val contactRecord = ContactRecord(
            name = contact.name,
            email = contact.email,
            message = contact.message
          )
          
          // Guardar en la base de datos de forma asíncrona
          context.pipeToSelf(repository.save(contactRecord)) {
            case Success(savedContact) => ContactSaved(savedContact, replyTo)
            case Failure(exception) => ContactSaveFailed(exception, replyTo)
          }
          
          Behaviors.same

        case ContactSaved(savedContact, replyTo) =>
          context.log.info(s"Contact saved with ID: ${savedContact.id}")
          replyTo ! ContactSubmitted(savedContact.id.getOrElse(0L).toString)
          Behaviors.same

        case ContactSaveFailed(exception, replyTo) =>
          context.log.error(s"Failed to save contact: ${exception.getMessage}", exception)
          replyTo ! ContactError(s"Database error: ${exception.getMessage}")
          Behaviors.same
      }
    }
  }
}
