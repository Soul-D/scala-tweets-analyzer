package bezh.tweets

import com.danielasfregola.twitter4s.TwitterRestClient
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class TwitterTest extends WordSpec with Matchers {

  "getTweets" should {
    "get tweets by paging through them" in {
      val client    = TwitterRestClient()
      val tweetsNum = 400
      val tweets =
        Await.result(
          Functions.getTweets("odersky", tweetsNum, client),
          Duration.Inf
        )
      tweets.size shouldBe tweetsNum
    }
  }
}
