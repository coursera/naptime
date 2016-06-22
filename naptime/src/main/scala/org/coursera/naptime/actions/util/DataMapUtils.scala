package org.coursera.naptime.actions.util

import com.linkedin.data.DataList
import com.linkedin.data.DataMap

/**
 * Some utilities to ensure whole trees of DataMaps are mutable.
 */
object DataMapUtils {
  // TODO: Profile for performance.
  def ensureMutable(obj: AnyRef): AnyRef = {
    obj match {
      case list: DataList => ensureMutableList(list)
      case map: DataMap => ensureMutableMap(map)
      case _ => obj
    }
  }

  def ensureMutableList(list: DataList): DataList = {
    if (list.isMadeReadOnly) {
      val mutableList = new DataList(list.size())
      val iterator = list.iterator()
      while (iterator.hasNext) {
        val i = iterator.next()
        mutableList.add(ensureMutable(i))
      }
      mutableList
    } else {
      for (i <- 0 until list.size) {
        list.set(i, ensureMutable(list.get(i)))
      }
      list
    }
  }

  def ensureMutableMap(map: DataMap): DataMap = {
    // TODO: check to see if we can skip doing a pessimistic copy.
    val mutableMap = new DataMap()
    val iterator = map.entrySet().iterator()
    while (iterator.hasNext) {
      val entry = iterator.next()
      mutableMap.put(entry.getKey, ensureMutable(entry.getValue))
    }
    mutableMap
  }
}
