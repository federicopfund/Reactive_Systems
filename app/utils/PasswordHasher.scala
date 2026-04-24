package utils

import org.mindrot.jbcrypt.BCrypt

/**
 * Utilidad para generar hashes de contraseñas
 * 
 * Uso:
 * sbt console
 * scala> import utils.PasswordHasher
 * scala> PasswordHasher.hashPassword("mi_contraseña")
 * scala> PasswordHasher.checkPassword("mi_contraseña", hash)
 */
object PasswordHasher {
  
  /**
   * Genera un hash BCrypt de una contraseña
   */
  def hashPassword(password: String): String = {
    BCrypt.hashpw(password, BCrypt.gensalt(10))
  }
  
  /**
   * Verifica si una contraseña coincide con un hash
   */
  def checkPassword(password: String, hash: String): Boolean = {
    BCrypt.checkpw(password, hash)
  }
  
  /**
   * Genera múltiples hashes para testing
   */
  def generateMultiple(password: String, count: Int = 3): Unit = {
    println(s"Generando $count hashes para: '$password'\n")
    (1 to count).foreach { i =>
      val hash = hashPassword(password)
      println(s"Hash #$i:")
      println(hash)
      println()
    }
  }
  
  def main(args: Array[String]): Unit = {
    if (args.length == 0) {
      println("Uso: PasswordHasher <contraseña>")
      println("\nEjemplos:")
      println("  sbt \"runMain utils.PasswordHasher admin123\"")
      println("  sbt \"runMain utils.PasswordHasher mySecurePassword\"")
      System.exit(1)
    }
    
    val password = args(0)
    val hash = hashPassword(password)
    
    println("=" * 80)
    println("Password Hash Generator")
    println("=" * 80)
    println()
    println(s"Password: $password")
    println(s"Hash:     $hash")
    println()
    println("SQL para insertar admin:")
    println("=" * 80)
    println(s"""
INSERT INTO admins (username, email, password_hash, role) 
VALUES ('nuevo_admin', 'admin@example.com', '$hash', 'admin');
    """.trim)
    println()
    println("Verificación:")
    println("=" * 80)
    val check = checkPassword(password, hash)
    println(s"¿Hash válido?: $check")
    println()
  }
}
