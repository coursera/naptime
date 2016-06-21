package org.coursera.naptime.actions.util

import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class DataMapUtilsTests extends AssertionsForJUnit {

  @Test
  def mutableMapUnchanged(): Unit = {
    val myMap = new DataMap()
    assert(myMap == DataMapUtils.ensureMutable(myMap))
  }

  @Test
  def mutableNonEmptyMapUnchanged(): Unit = {
    val myMap = new DataMap()
    myMap.put("1", new Integer(1))
    myMap.put("2", new Integer(2))
    assert(myMap == DataMapUtils.ensureMutable(myMap))
  }

  @Test
  def nonComplexUnchanged(): Unit = {
    val myString = "myString"
    assert(myString eq DataMapUtils.ensureMutable(myString))

    val myInt = new Integer(2)
    assert(myInt eq DataMapUtils.ensureMutable(myInt))

  }

  @Test
  def mutableListUnchanged(): Unit = {
    val myList = new DataList()
    assert(myList eq DataMapUtils.ensureMutable(myList))
  }

  @Test
  def mutableNonEmptyListUnchanged(): Unit = {
    val myList = new DataList()
    myList.add("1")
    myList.add(new Integer(2))
    assert(myList eq DataMapUtils.ensureMutable(myList))
  }

  @Test
  def ensureImmutableListIsMutable(): Unit = {
    val myList = new DataList()
    myList.setReadOnly()
    val mutableList = DataMapUtils.ensureMutable(myList)
    assert(!mutableList.asInstanceOf[DataList].isMadeReadOnly)
  }

  @Test
  def ensureNestedImmutableIsMadeMutable(): Unit = {
    val immutable = new DataMap()
    immutable.put("1", "one")
    immutable.setReadOnly()

    val wrappingList = new DataList()
    wrappingList.add(immutable)

    val wrappingMap = new DataMap()
    wrappingMap.put("list", wrappingList)

    val fullyMutable = DataMapUtils.ensureMutable(wrappingMap)
    assert(fullyMutable.isInstanceOf[DataMap])
    assert(!fullyMutable.asInstanceOf[DataMap].isMadeReadOnly)
    assert(wrappingMap == fullyMutable)

    val mutableList = fullyMutable.asInstanceOf[DataMap].get("list")
    assert(mutableList.isInstanceOf[DataList])
    assert(!mutableList.asInstanceOf[DataList].isMadeReadOnly)
    assert(mutableList == wrappingList)
    assert(mutableList eq wrappingList)

    val innerMap = mutableList.asInstanceOf[DataList].get(0)
    assert(innerMap.isInstanceOf[DataMap])
    assert(innerMap == immutable)
    assert(!innerMap.asInstanceOf[DataMap].isMadeReadOnly)
    assert(innerMap.asInstanceOf[DataMap].get("1") == "one")
  }
}
