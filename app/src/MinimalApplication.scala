package app

import app.pgconn.insertProfile
import cask.Response

import scala.util.{Failure, Success, Try}

object MinimalApplication extends cask.MainRoutes {
  override def host: String = "0.0.0.0"

  @cask.get("/")
  def index() = {
    "OK"
  }

  @cask.postJson("/profile")
  def hello(
      access_token: ujson.Value,
      liffid: ujson.Value
  ) = {
    Try(
      requests.get(
        s"https://api.line.me/oauth2/v2.1/verify?access_token=${access_token.value}"
      )
    ).filter(value => value.statusCode == 200)
      .flatMap(_ =>
        Try(
          requests.get(
            "https://api.line.me/v2/profile",
            headers = Map("Authorization" -> s"Bearer ${access_token.value}")
          )
        )
      )
      .map(value => ujson.read(value.text())) match {
      case Success(value) =>
        // yorktodo:workaround 如果insert失敗應該要update
        insertProfile(
          value("userId").str,
          value("displayName").str,
          value("pictureUrl").str,
          liffid.str
        )
        cask.Response(
          Map(
            "userId" -> value("userId").str,
            "displayName" -> value("displayName").str,
            "pictureUrl" -> value("pictureUrl").str
          ),
          statusCode = 200
        )
      case Failure(_) =>
        cask.Response(Map("error" -> "Forbidden"), statusCode = 403)
    }
  }
  initialize()
}
