package com.example

import scopt._
import com.danielasfregola.twitter4s.TwitterRestClient
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await

object Main {

  private val defaultLimit = 1000

  case class Config(
      tweetsLimit: Int = defaultLimit,
      screenName: String = "",
      showHelp: Boolean = false,
      tzAutoAdjustment: Boolean = true,
      friendsAnalysis: Boolean = false,
      utcOffset: Long = 0
  )

  val parser = new OptionParser[Config]("tweets-analyzer") {
    opt[Unit]('h', "help").action((_, c) => c.copy(showHelp = true))

    opt[Int]('l', "limit")
      .valueName("N")
      .action((n, c) => c.copy(tweetsLimit = n))
      .text(s"limit the number of tweets to retrieve (default=$defaultLimit)")

    opt[String]('n', "name")
      .valueName("screen_name")
      .required()
      .action((name, c) => c.copy(screenName = name))
      .text("target twitter handle screen_name")

    opt[String]('f', "filter")
      .valueName("FILTER")
      .text("filter by source: android|ios|web")

    opt[Unit]("no-timezone")
      .text("removes the timezone auto-adjustment (default is UTC)")
      .action((_, c) => c.copy(tzAutoAdjustment = false))

    opt[Long]("utc-offset")
      .valueName("UTC_OFFSET")
      .action((offset, c) => c.copy(utcOffset = offset))
      .text("manually apply a timezone offset (in seconds)")

    opt[Unit]("friends")
      .action((_, c) => c.copy(friendsAnalysis = true))
      .text("will perform quick friends analysis based on lang and " +
        "timezone (rate limit = 15 requests)")
  }

  def start(cfg: Config): Unit = {
    val restClient = TwitterRestClient()

    println(s"Getting ${cfg.screenName} account data...")
    val future = restClient.user(cfg.screenName).map { user =>
      println(s"lang: ${user.data.lang}")
      println(s"geo_enabled: ${user.data.geo_enabled}")
      println(s"time_zone: ${user.data.time_zone}")
      println(s"utc_offset: ${user.data.utc_offset}")
    }
    Await.result(future, 5.seconds)
  }

  def main(args: Array[String]): Unit = {
    val config = parser.parse(args, Config())
    config match {
      case Some(cfg) =>
        if (cfg.showHelp) {
          parser.showUsage()
        } else {
          start(cfg)
        }
      case _ =>
    }
  }
}
