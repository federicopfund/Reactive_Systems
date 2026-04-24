package models

import java.time.Instant

case class UserBadge(
  id: Option[Long] = None,
  userId: Long,
  badgeKey: String,
  awardedAt: Instant = Instant.now()
)

object BadgeDefinition {
  case class Badge(key: String, name: String, description: String, icon: String)

  val all: Seq[Badge] = Seq(
    Badge("first_publication",   "Primera Publicación",    "Publicaste tu primer artículo",               "📝"),
    Badge("five_publications",   "Escritor Activo",        "Publicaste 5 artículos",                      "✍️"),
    Badge("ten_publications",    "Autor Prolífico",        "Publicaste 10 artículos",                     "📚"),
    Badge("first_approved",      "Aprobado",               "Tu primera publicación fue aprobada",          "✅"),
    Badge("ten_likes",           "Popular",                "Recibiste 10 reacciones en total",             "⭐"),
    Badge("fifty_likes",         "Estrella",               "Recibiste 50 reacciones en total",             "🌟"),
    Badge("hundred_views",       "Visible",                "Tus publicaciones alcanzaron 100 vistas",      "👁️"),
    Badge("five_hundred_views",  "Influyente",             "Tus publicaciones alcanzaron 500 vistas",      "🔭"),
    Badge("first_comment",       "Conversador",            "Dejaste tu primer comentario",                 "💬"),
    Badge("ten_comments",        "Participativo",          "Dejaste 10 comentarios",                       "🗣️")
  )

  def get(key: String): Option[Badge] = all.find(_.key == key)
}
