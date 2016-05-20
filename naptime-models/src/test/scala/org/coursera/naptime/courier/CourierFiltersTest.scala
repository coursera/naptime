package org.coursera.naptime.courier

import org.coursera.courier.templates.DataTemplates
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class CourierFiltersTest extends AssertionsForJUnit {

  import CourierTestFixtures._

  @Test
  def testFilterUnrecognizedFields(): Unit = {
    val filtered = CourierFilters.filterUnrecognizedFields(
      DataTemplates.readDataMap(pegasusUnionJsonWithUnrecognizedField),
      pegasusUnionSchema)

    CourierAssertions.assertSameJson(filtered, pegasusUnionJson)
  }

}
