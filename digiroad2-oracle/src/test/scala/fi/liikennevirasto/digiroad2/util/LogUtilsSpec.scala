package fi.liikennevirasto.digiroad2.util

import org.mockito.ArgumentMatchers
import org.mockito.Mockito.verify
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import org.slf4j.Logger

import java.util.concurrent.atomic.AtomicInteger

class LogUtilsSpec extends FunSuite with Matchers {
  val logger = MockitoSugar.mock[Logger]

  test("test logArrayProgress when processing arrays parallel") {
    val array = (1 to 100).toList
    val groupSizeForParallelRun = 2
    val maximumParallelismLevel = 5

    val expectedLogMessages = List(
      "Processing changes is 10% complete",
      "Processing changes is 20% complete",
      "Processing changes is 30% complete",
      "Processing changes is 40% complete",
      "Processing changes is 50% complete",
      "Processing changes is 60% complete",
      "Processing changes is 70% complete",
      "Processing changes is 80% complete",
      "Processing changes is 90% complete",
      "Processing changes is 100% complete"
    )

    simpleParallelLoop(array, groupSizeForParallelRun, maximumParallelismLevel, logger)

    // Verify that the logger's info method was called with the expected log messages exactly once
    expectedLogMessages.foreach { message =>
      verify(logger).info(ArgumentMatchers.contains(message))
    }

  }

  def simpleParallelLoop(numbersToProcess: Seq[Int], groupSizeForParallelRun: Int, maximumParallelismLevel: Int, logger: Logger): Unit = {
    val numbersGrouped = numbersToProcess.grouped(groupSizeForParallelRun).toList.par
    val totalTasks = numbersGrouped.size
    val totalItems = numbersToProcess.size
    val level = if (totalTasks < maximumParallelismLevel) totalTasks else maximumParallelismLevel
    println(s"Change groups: $totalTasks, parallelism level used: $level")

    val processedLinksCounter = new AtomicInteger(0)
    val progressTenPercentCounter = new AtomicInteger(0)

    new Parallel().operation(numbersGrouped, level) { tasks =>
      tasks.flatMap { elems =>
        elems.map(elem => {
          synchronized {
            val totalChangesProcessed = processedLinksCounter.getAndIncrement()
            val currentTenPercent = LogUtils.logArrayProgress(logger, "Processing changes", totalItems, totalChangesProcessed, progressTenPercentCounter.get())
            progressTenPercentCounter.set(currentTenPercent)
          }
        })
      }
    }
  }
}
