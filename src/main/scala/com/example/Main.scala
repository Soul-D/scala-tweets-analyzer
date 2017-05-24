package com.example

import java.lang.Math.{ abs, min }

import com.danielasfregola.twitter4s.entities.{ Tweet, User, UserMention }
import scopt._
import com.danielasfregola.twitter4s.{
  TwitterRestClient,
  TwitterStreamingClient
}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future }
import com.example.Config._

case class Config(
  tweetsLimit: Int = defaultLimit,
  screenName: String = "",
  showHelp: Boolean = false,
  tzAutoAdjustment: Boolean = true,
  friendsAnalysis: Boolean = false,
  utcOffset: Option[Long] = None
)
object Config {
  val defaultLimit = 1000
}

object Functions {

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
      .action((offset, c) => c.copy(utcOffset = Some(offset)))
      .text("manually apply a timezone offset (in seconds)")

    opt[Unit]("friends")
      .action((_, c) => c.copy(friendsAnalysis = true))
      .text("will perform quick friends analysis based on lang and " +
        "timezone (rate limit = 15 requests)")
  }

  def getTweets(
    name: String,
    tweetsNumAtLeast: Int,
    client: TwitterRestClient,
    maxId: Option[Long] = None,
    acc: Seq[Tweet] = Nil
  ): Future[Seq[Tweet]] = {
    if (acc.size < tweetsNumAtLeast) {
      val limit = min(200, abs(tweetsNumAtLeast - acc.size))
      client
        .userTimelineForUser(name, max_id = maxId, count = limit)
        .flatMap { tweets =>
          if (tweets.data.isEmpty) {
            Future.successful(acc)
          } else {
            getTweets(
              name = name,
              tweetsNumAtLeast = tweetsNumAtLeast,
              client = client,
              maxId = tweets.data.lastOption.map(_.id - 1),
              acc = acc ++ tweets.data
            )
          }
        }
    } else {
      Future.successful[Seq[Tweet]](acc)
    }
  }

  def start(cfg: Config): Unit = {
    val restClient = TwitterRestClient()
    val streamingClient = TwitterStreamingClient()

    println(s"Getting ${cfg.screenName} account data...")
    val userFuture = restClient.user(cfg.screenName)
    val future = userFuture.flatMap { user =>
      val tweetsNum = Math.min(cfg.tweetsLimit, user.data.statuses_count)
      val tweetsFuture = getTweets(cfg.screenName, tweetsNum, restClient)
      tweetsFuture.map(user -> _)
    }

    cfg.utcOffset.foreach(offset =>
      println(s"Applying timezone offset $offset"))

    val (user, tweets) = Await.result(future, 1.minute)
    //      println(s"lang: ${user.data.lang}")
    //      println(s"geo_enabled: ${user.data.geo_enabled}")
    //      println(s"time_zone: ${user.data.time_zone}")
    //      println(s"utc_offset: ${user.data.utc_offset}")

    println(s"statuses count: ${user.data.statuses_count}\n")

    var retweetedUsers = Vector.empty[User]
    var mentionedUsers = Vector.empty[UserMention]

    tweets.foreach { tweet =>
      tweet.retweeted_status.foreach { retweet =>
        retweet.user.foreach(user => retweetedUsers :+= user)
      }
      tweet.entities
        .map(_.user_mentions)
        .getOrElse(Nil)
        .foreach(userMention => mentionedUsers :+= userMention)
    }

    println("most retweeted users: ")
    val mostRetweetedUsers = retweetedUsers
      .groupBy(_.id)
      .map {
        case (_, users) => (users.head.screen_name, users.size)
      }
      .toList
      .sortBy { case (_, count) => -count }
      .take(10)
    mostRetweetedUsers.foreach(println)

    println("most mentioned users:")
    val mostMentionedUsers = mentionedUsers
      .groupBy(_.id)
      .map {
        case (id, mentions) => (mentions.head.screen_name, mentions.size)
      }
      .toList
      .sortBy { case (_, count) => -count }
      .take(10)
    mostMentionedUsers.foreach(println)
  }
}

object Main {
  import Functions._

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
