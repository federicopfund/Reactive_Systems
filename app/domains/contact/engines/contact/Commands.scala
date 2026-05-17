package domains.contact.engines.contact

import akka.actor.typed.ActorRef
import domains.contact.models.ContactRecord

/**
 * Protocolo de comandos del ContactEngine.
 *
 * Dos niveles de visibilidad:
 *   - [[SubmitContact]]     → API pública (adapters, guardian).
 *   - [[ContactSaved]]      → `private[engines]`, resultado positivo del pipeline.
 *   - [[ContactSaveFailed]] → `private[engines]`, fallo del pipeline.
 */

/** Modelo DTO para el formulario de contacto. */
case class Contact(name: String, email: String, message: String)

sealed trait ContactCommand

// ── API pública ──────────────────────────────────────────────────────────────

case class SubmitContact(
  contact: Contact,
  replyTo: ActorRef[ContactResponse]
) extends ContactCommand

// ── Protocolo interno ────────────────────────────────────────────────────────

private[engines] case class ContactSaved(
  savedContact: ContactRecord,
  replyTo:      ActorRef[ContactResponse]
) extends ContactCommand

private[engines] case class ContactSaveFailed(
  exception: Throwable,
  replyTo:   ActorRef[ContactResponse]
) extends ContactCommand
