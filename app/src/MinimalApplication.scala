package app

import app.pgconn.{
  deleteFavoriteByInfoId,
  getAccountIdByLineId,
  getAllFavorites,
  insertAccountIfLineProfileNotExist,
  insertFavorite,
  insertProfile
}
import scala.util.Properties
import scala.Right
import scala.collection.mutable.ArrayBuffer
import scala.util.Try
import java.time.Instant;
import java.time.Duration;

final case class RequestException(
    private val message: String = "",
    private val cause: Throwable = None.orNull
) extends Exception(message, cause)

object MinimalApplication extends cask.MainRoutes {
  override val host: String = "0.0.0.0"
  val lastTime: Instant = Instant.now();

  @cask.get("/health")
  def index() = {
    println("get health check")
    val difference: Duration = Duration.between(lastTime, Instant.now());
    if (difference.getSeconds() > 120) { // yorktodo workaround: too many sql client
      println("restart server workaround")
      System.exit(1)
    }
    "OK"
  }

  @cask.postJson("/accounts/:accountId/favorites")
  def createFavorite(faceId: ujson.Value, accountId: String) =
    insertFavorite(faceId.num.toInt, accountId) match {
      case Right(_) =>
        //yorktodo思考如何回傳空值
        cask.Response(
          Map("status" -> "OK"),
          statusCode = 200
        )
      case Left(_) =>
        cask.Response(Map("status" -> "Forbidden"), statusCode = 403)
    }

  //yorktodo:infoId應該要改成用favoriteId，但get也要記得回傳favoriteId
  @cask.delete("/accounts/:accountId/favorites/:infoId")
  def deleteFavorite(accountId: String, infoId: String) = {
    //yorktodo思考如何回傳成功與否
    deleteFavoriteByInfoId(accountId, infoId) match {
      case Right(result) =>
        result match {
          case 0 => "Delete 0 favorite"
          case 1 => "Delete 1 favorite"
        }
      case Left(err) => {
        "Forbidden"
      }
    }
  }

  @cask.getJson("/accounts/:accountId/favorites")
  def getAllFavorite(accountId: String) = {
    getAllFavorites(accountId) match {
      case Right(value) =>
        cask.Response(
          Map(
            "favoriteIds" -> value
          ),
          statusCode = 200
        )
      case Left(error) =>
        System.err.println("get favorites error: ", error)
        cask.Response(
          //yorktodo:思考回傳值
          Map(
            "favoriteIds" -> new ArrayBuffer[ujson.Obj]()
          ),
          statusCode = 403
        )
    }
  }

  // yorktodo:創建完畢後回傳實體
  @cask.postJson("/profile")
  def createProfile(
      access_token: ujson.Value,
      liffid: ujson.Value
  ) = {
    Try(
      requests.get(
        s"https://api.line.me/oauth2/v2.1/verify?access_token=${access_token.value}"
      )
    ).toEither
      .flatMap(value =>
        Either.cond(
          value.statusCode == 200 && ujson
            .read(value.text())("client_id")
            .str == "1655529572",
          value,
          RequestException("RequestError")
        )
      )
      .flatMap(_ =>
        Try(
          requests.get(
            "https://api.line.me/v2/profile",
            headers = Map("Authorization" -> s"Bearer ${access_token.value}")
          )
        ).toEither
      )
      .map(response => ujson.read(response.text()))
      .map(profileResponse =>
        Map(
          "userId" -> profileResponse("userId").str,
          "displayName" -> profileResponse("displayName").str,
          "pictureUrl" -> Try(profileResponse("pictureUrl").str)
            .getOrElse("")
        )
      )
      .flatMap(profileResponse =>
        // yorktodo:workaround 如果insert失敗應該要update
        insertAccountIfLineProfileNotExist(profileResponse("userId"))
          .flatMap {
            case Some(accountId) =>
              insertProfile(
                profileResponse("userId"),
                profileResponse("displayName"),
                profileResponse("pictureUrl"),
                liffid.str,
                accountId
              ).map(_ => (profileResponse, accountId))
            case None =>
              getAccountIdByLineId(profileResponse("userId"))
                .map(accountId => (profileResponse, accountId))
          }
      ) match {
      case Right((profileResponse, accountId)) =>
        cask.Response(
          Map(
            "userId" -> profileResponse("userId"),
            "displayName" -> profileResponse("displayName"),
            "pictureUrl" -> profileResponse("pictureUrl"),
            "accountId" -> accountId
          ),
          statusCode = 200
        )
      case Left(_) =>
        cask.Response(Map("error" -> "Forbidden"), statusCode = 403)
    }
  }

  initialize()
}
