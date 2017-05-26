package bezh.tweets

import Config._

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
