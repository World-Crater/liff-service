package app

import java.sql.{DriverManager, ResultSet, Statement}
import scala.util.{Try}
import scala.util.Properties
import scala.collection.mutable.ArrayBuffer

final case class SQLException(
    private val message: String = "",
    private val cause: Throwable = None.orNull
) extends Exception(message, cause)

class FaceInfo(
    id: Int,
    name: String,
    preview: String,
    detail: String,
    remanization: String
) {
  def Id: Int = id
  def Name: String = name
  def Preview: String = preview
  def Detail: String = detail
  def Remanization: String = remanization
  def ToMap(): Map[String, Any] = {
    Map(
      "id" -> Id,
      "name" -> Name,
      "preview" -> Preview,
      "detail" -> Detail,
      "remanization" -> Remanization
    )
  }
}

object pgconn extends App {
  classOf[org.postgresql.Driver]
  private def conn =
    DriverManager.getConnection(
      s"jdbc:postgresql://${Properties.envOrElse("DB_HOST", "localhost:5432")}/${Properties
        .envOrElse("DB_NAME", "messfar")}?user=${Properties.envOrElse("DB_USER", "yorklin")}&password=${Properties
        .envOrElse("DB_PASSWORD", "nNpR3VVVxsI8S1tfGNBo")}"
    )
  // yorktodo測試速錯
  private def stm =
    Try(
      conn.createStatement(
        ResultSet.TYPE_FORWARD_ONLY,
        ResultSet.CONCUR_READ_ONLY
      )
    ).toEither
  if (stm.isLeft) {
    conn.close()
    System.exit(1)
  }

  def insertProfile(
      userId: String,
      displayName: String,
      pictureUrl: String,
      liffid: String,
      accountId: String
  ): Either[Throwable, Boolean] =
    stm.flatMap(executor =>
      Try(
        executor.execute(
          s"INSERT INTO line_profile (id,userId,displayName,pictureUrl,liffId,account_id) SELECT uuid_generate_v4(),'$userId','$displayName','$pictureUrl','$liffid', '$accountId' WHERE NOT EXISTS (SELECT 1 FROM line_profile WHERE userId = '$userId')"
        )
      ).toEither
    )

  def insertAccountIfLineProfileNotExist(
      userId: String
  ): Either[Throwable, Option[String]] =
    stm
      .flatMap(executor =>
        Try(
          executor.executeQuery(
            s"INSERT INTO account SELECT uuid_generate_v4() WHERE NOT EXISTS (SELECT 1 FROM line_profile WHERE userId = '$userId') RETURNING id"
          )
        ).toEither
      )
      .flatMap(rs => {
        var result = ""
        while (rs.next) {

          result = rs.getString("id")
        }
        if (result != "") Right(Some(result)) else Right(None)
      })

  def getAccountIdByLineId(lineId: String): Either[Throwable, String] =
    stm
      .flatMap(executor =>
        Try(
          executor.executeQuery(
            s"SELECT account_id FROM line_profile WHERE userId = '$lineId'"
          )
        ).toEither
      )
      .flatMap(rs => {
        var result = ""
        while (rs.next) {
          result = rs.getString("account_id")
        }
        Either.cond(
          result != "",
          result,
          SQLException("SelectError")
        )
      })

  def insertFavorite(
      infoId: Int,
      accountId: String
  ): Either[Throwable, Boolean] =
    stm.flatMap(executor =>
      Try(
        executor.execute(
          s"INSERT INTO facefavorites (id,info_id,account_id) VALUES (uuid_generate_v4(),'$infoId','$accountId')"
        )
      ).toEither
    )

  def getAllFavorites(
      accountId: String
  ): Either[Throwable, ArrayBuffer[ujson.Obj]] =
    stm
      .flatMap(executor =>
        Try(
          executor.executeQuery(
            s"SELECT infos.id, infos.name, infos.preview, infos.detail, infos.romanization FROM facefavorites LEFT JOIN faceinfos AS infos ON facefavorites.info_id = infos.id WHERE account_id = '$accountId'"
          )
        ).toEither
      )
      .map(rs => {
        val resultList =
          new ArrayBuffer[ujson.Obj](
            rs.getMetaData().getColumnCount()
          )
        while (rs.next()) {
          // yorktodo可以封裝方法
          resultList += ujson.Obj(
            "id" -> rs.getInt("id"),
            "name" -> rs.getString("name"),
            "preview" -> rs.getString("preview"),
            "detail" -> rs.getString("detail"),
            "romanization" -> (
                (value: String) => (if (value == null) "" else value)
            )(rs.getString("romanization"))
          )
        }
        resultList
      })

  def deleteFavoriteByInfoId(
      accountId: String,
      infoId: String
  ): Either[Throwable, Int] =
    stm.flatMap(executor =>
      Try(
        executor.executeUpdate(
          s"DELETE FROM facefavorites WHERE account_id = '$accountId' AND info_id = '$infoId'"
        )
      ).toEither
    )

  def close() = conn.close()
}
