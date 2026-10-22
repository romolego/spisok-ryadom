package com.spisokryadom.app.util

/** Matching only changes priority; a typed value never hides the rest of the dictionary. */
object UnitChoices {
 fun ranked(values:List<String>,query:String):List<String> {
  val all=values.filter{it.isNotBlank()}.distinctBy{FuzzySearch.normalize(Quantities.unit(it))}
  if(query.isBlank())return all
  val matched=FuzzySearch.filterAndSort(all,query)
  return matched+all.filter{it !in matched}
 }
}
