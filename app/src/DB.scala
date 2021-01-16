package app

import java.sql.{Statement, DriverManager, ResultSet}
import scala.util.{Failure, Success, Try}
import scala.util.Properties

object pgconn extends App {
  classOf[org.postgresql.Driver]
  private def conn =
    DriverManager.getConnection(
      s"jdbc:postgresql://${Properties.envOrElse("DB_HOST", "localhost:5432")}/${Properties
        .envOrElse("DB_NAME", "")}?user=${Properties.envOrElse("DB_USER", "")}&password=${Properties
        .envOrElse("DB_PASSWORD", "")}"
    )
  private def stm: Try[Statement] =
    Try(
      conn.createStatement(
        ResultSet.TYPE_FORWARD_ONLY,
        ResultSet.CONCUR_READ_ONLY
      )
    )
  def insertProfile(
      userId: String,
      displayName: String,
      pictureUrl: String,
      liffid: String
  ) =
    stm match {
      // yorktodo:workaround: 需檢查是否insert成功
      case Success(executor) =>
        executor.execute(
          s"INSERT INTO line_profile (id,userId,displayName,pictureUrl,liffId) SELECT uuid_generate_v4(),'$userId','$displayName','$pictureUrl','$liffid' WHERE NOT EXISTS (SELECT 1 FROM line_profile WHERE userId = '$userId')"
        )
      // yorktodo:workaround: 需丟出error
      case Failure(_) => conn.close()
    }
}
