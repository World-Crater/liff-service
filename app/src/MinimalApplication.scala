package app

import app.pgconn.{
  deleteFavoriteByInfoId,
  getAccountIdByLineId,
  getAllFavorites,
  insertAccountIfLineProfileNotExist,
  insertFavorite,
  insertProfile
}

import scala.collection.mutable.ArrayBuffer
import scala.util.Try

final case class RequestException(
    private val message: String = "",
    private val cause: Throwable = None.orNull
) extends Exception(message, cause)

object MinimalApplication extends cask.MainRoutes {
  override def host: String = "0.0.0.0"

  @cask.get("/")
  def index() = {
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
      case Left(_) =>
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
  ) =
    Try(
      requests.get(
        s"https://api.line.me/oauth2/v2.1/verify?access_token=${access_token.value}"
      )
    ).toEither
      .flatMap(value =>
        Either.cond(
          value.statusCode == 200,
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
      .flatMap(profileResponse =>
        // yorktodo:workaround 如果insert失敗應該要update
        insertAccountIfLineProfileNotExist(profileResponse("userId").str)
          .flatMap {
            case Some(accountId) =>
              insertProfile(
                profileResponse("userId").str,
                profileResponse("displayName").str,
                profileResponse("pictureUrl").str,
                liffid.str,
                accountId
              ).map(_ => (profileResponse, accountId))
            case None =>
              getAccountIdByLineId(profileResponse("userId").str)
                .map(accountId => (profileResponse, accountId))
          }
      ) match {
      case Right((profileResponse, accountId)) =>
        cask.Response(
          Map(
            "userId" -> profileResponse("userId").str,
            "displayName" -> profileResponse("displayName").str,
            "pictureUrl" -> profileResponse("pictureUrl").str,
            "accountId" -> accountId
          ),
          statusCode = 200
        )
      case Left(_) =>
        cask.Response(Map("error" -> "Forbidden"), statusCode = 403)
    }

  initialize()
}
