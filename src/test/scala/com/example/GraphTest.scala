package com.example

import org.scalatest.{ Matchers, WordSpec }

class GraphTest extends WordSpec with Matchers {
  "printCharts" should {
    "print the graph" in {
      Functions.printCharts(
        Map(
          0 -> 120,
          1 -> 135,
          2 -> 85,
          3 -> 49,
          4 -> 21,
          5 -> 15,
          6 -> 1,
          7 -> 1,
          8 -> 4,
          9 -> 2,
          10 -> 3,
          11 -> 4,
          12 -> 5,
          13 -> 14,
          14 -> 61,
          15 -> 51,
          16 -> 95,
          17 -> 122,
          18 -> 156,
          19 -> 167,
          20 -> 143,
          21 -> 157,
          22 -> 125,
          23 -> 145
        ),
        ""
      )
    }
  }
}
