package com.example

import java.lang.Math.{ abs, min }
import java.net.URL
import java.util.Calendar

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
import org.joda.time.DateTime
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter }

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

    println(s"Getting @${cfg.screenName} account data...")
    val userFuture = restClient.user(cfg.screenName)
    val future = userFuture.flatMap { user =>
      println(s"[+] lang           : ${user.data.lang}")
      println(s"[+] geo_enabled    : ${user.data.geo_enabled}")
      println(s"[+] time_zone      : ${user.data.time_zone}")
      println(s"[+] utc_offset     : ${user.data.utc_offset}")
      println(s"[+] statuses count : ${user.data.statuses_count}")

      val tweetsNum = Math.min(cfg.tweetsLimit, user.data.statuses_count)
      println(s"[+] Retrieving last $tweetsNum tweets...")
      val tweetsFuture = getTweets(cfg.screenName, tweetsNum, restClient)
      tweetsFuture.map { tweets =>
        val lastCreatedAt = new DateTime(tweets.last.created_at.getTime)
        val firstCreatedAt = new DateTime(tweets.head.created_at.getTime)
        val format = DateTimeFormat.shortDateTime()
        println(
          s"[+] Downloaded ${tweets.size} tweets from ${
            lastCreatedAt.toString(
              format
            )
          } to ${firstCreatedAt.toString(format)}"
        )
        (user, tweets)
      }
    }

    cfg.utcOffset.foreach(offset =>
      println(s"Applying timezone offset $offset"))

    var retweetedUsers = Vector.empty[User]
    var mentionedUsers = Vector.empty[UserMention]
    var mentionedDomains = Vector.empty[String]
    var hoursOfDay = Map.empty[Int, Int]
    var daysOfWeek = Map.empty[Int, Int]

    val (_, tweets) = Await.result(future, 1.minute)

    def processTweet(tweet: Tweet): Unit = {
      tweet.retweeted_status.foreach { retweet =>
        retweet.user.foreach(user => retweetedUsers :+= user)
      }
      tweet.entities
        .map(_.user_mentions)
        .getOrElse(Nil)
        .foreach(userMention => mentionedUsers :+= userMention)

      val urls = tweet.entities.map(_.urls).getOrElse(Nil)
      val domains = urls.flatMap { url =>
        var dom = new URL(url.expanded_url).getHost
        if (dom.startsWith("www."))
          dom = dom.drop(4)

        if (dom == "twitter.com") None
        else Some(dom)
      }
      mentionedDomains ++= domains

      val dateTime = new DateTime(tweet.created_at.getTime)
      val hour = dateTime.getHourOfDay
      val dayOfWeek = dateTime.dayOfWeek().get()
      hoursOfDay += (hour -> (hoursOfDay.getOrElse(hour, 0) + 1))
      daysOfWeek += (dayOfWeek -> (daysOfWeek.getOrElse(dayOfWeek, 0) + 1))
    }

    tweets foreach processTweet

    print("[+] Most Retweeted Users: ")
    val mostRetweetedUsers = retweetedUsers
      .groupBy(_.id)
      .map {
        case (_, users) => (users.head.screen_name, users.size)
      }
      .toList
      .sortBy { case (_, count) => -count }
      .map { case (screenName, _) => screenName }
      .take(10)
    println(mostRetweetedUsers.mkString(", "))

    print("[+] Most Mentioned Users: ")
    val mostMentionedUsers = mentionedUsers
      .groupBy(_.id)
      .map {
        case (id, mentions) => (mentions.head.screen_name, mentions.size)
      }
      .toList
      .sortBy { case (_, count) => -count }
      .map { case (screenName, _) => screenName }
      .take(10)
    println(mostMentionedUsers.mkString(", "))

    print("[+] Most Referenced Domains:")
    val mostMentionedDomains = mentionedDomains
      .groupBy(identity)
      .map { case (domain, domains) => (domain, domains.size) }
      .toList
      .sortBy {
        case (_, count) => -count
      }
      .take(10)
    println(mostMentionedDomains.mkString(", "))

    printCharts(hoursOfDay, "Activity per hour (distribution)")
    printCharts(daysOfWeek, "Activity per week day (distribution)")
  }

  def getMean(values: Seq[Int]): Double =
    values.sum / values.length

  def getMedian(values: Seq[Int]): Double = {
    val middle = values.length / 2
    if (values.length % 2 == 1)
      values(middle)
    else {
      (values(middle - 1) + values(middle)) / 2.0
    }
  }

  def printCharts(data: Map[Int, Int], title: String): Unit = {
    val sorted = data.toList.sorted
    val mean = getMean(data.values.toSeq)
    val median = getMedian(data.values.toSeq)
    val lineWidth = 50
    val lineMean = getMean(1 to lineWidth)
    println(s"mean $mean median $median")

    println(title)
    println((1 to lineWidth).map(_ => "#").mkString)

    sorted.foreach {
      case (key, value) =>
        val valueColor =
          if (value <= mean * 0.66) "\033[32m"
          else if (value <= mean * 1.33) "\033[33m"
          else "\033[31m"

        val valueInLineUnits =
          Math.ceil((value / sorted.head._2.toDouble) * lineWidth).toInt

        val coloredLine = (1 to lineWidth)
          .map { i =>
            if (i >= valueInLineUnits)
              " "
            else if (i <= lineMean * 0.66)
              "\033[42m \033[0m"
            else if (i <= lineMean * 1.33)
              "\033[43m \033[0m"
            else if (i <= lineMean * 1.6)
              "\033[41m \033[0m"
            else " "
          }
          .mkString("")
        printf(s"$coloredLine $valueColor%3d\033[0m     %02d:00\n", value, key)
    }
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
