package org.coursera.naptime.courier

import com.linkedin.data.DataMap
import org.coursera.courier.templates.DataTemplates
import org.scalatest.junit.AssertionsForJUnit

object CourierAssertions extends AssertionsForJUnit {

  def assertSameJson(json: String, expectedJson: String): Unit = {
    assert(DataTemplates.readDataMap(json) === DataTemplates.readDataMap(expectedJson))
  }

  def assertSameJson(dataMap: DataMap, expectedJson: String): Unit = {
    assert(dataMap === DataTemplates.readDataMap(expectedJson))
  }

}
