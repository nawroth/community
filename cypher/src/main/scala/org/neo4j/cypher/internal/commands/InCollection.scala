/**
 * Copyright (c) 2002-2012 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.commands

import collection.Seq
import expressions.{Closure, Expression}
import org.neo4j.cypher.internal.symbols._
import collection.Map
import java.lang.{Iterable => JavaIterable}
import java.util.{Map => JavaMap}
import collection.JavaConverters._

abstract class InCollection(collection: Expression, id: String, predicate: Predicate)
  extends Predicate
  with CollectionSupport
  with Closure {
  def seqMethod[U](f: Seq[U]): ((U) => Boolean) => Boolean

  def isMatch(m: Map[String, Any]): Boolean = {
    val seq = makeTraversable(collection(m)).toSeq

    seqMethod(seq)(item => {
      val innerMap = m ++ Map(id -> item)
      predicate.isMatch(innerMap)
    })
  }

  def atoms: Seq[Predicate] = Seq(this)

  def name: String

  override def toString() = name + "(" + id + " in " + collection + " where " + predicate + ")"

  def containsIsNull = predicate.containsIsNull

  def filter(f: (Expression) => Boolean): Seq[Expression] = collection.filter(f) ++ predicate.filter(f)

  def assertInnerTypes(symbols: SymbolTable) {
    val innerType = collection.evaluateType(AnyCollectionType(), symbols).iteratedType
    predicate.assertTypes(symbols.add(id, innerType))
  }

  def symbolTableDependencies = symbolTableDependencies(collection, predicate, id)
}

case class AllInCollection(collection: Expression, symbolName: String, inner: Predicate) extends InCollection(collection, symbolName, inner) {
  def seqMethod[U](f: Seq[U]): ((U) => Boolean) => Boolean = f.forall _

  def name = "all"

  def rewrite(f: (Expression) => Expression) = AllInCollection(collection.rewrite(f), symbolName, inner.rewrite(f))
}

case class AnyInCollection(collection: Expression, symbolName: String, inner: Predicate) extends InCollection(collection, symbolName, inner) {
  def seqMethod[U](f: Seq[U]): ((U) => Boolean) => Boolean = f.exists _

  def name = "any"

  def rewrite(f: (Expression) => Expression) = AnyInCollection(collection.rewrite(f), symbolName, inner.rewrite(f))
}

case class NoneInCollection(collection: Expression, symbolName: String, inner: Predicate) extends InCollection(collection, symbolName, inner) {
  def seqMethod[U](f: Seq[U]): ((U) => Boolean) => Boolean = x => !f.exists(x)

  def name = "none"

  def rewrite(f: (Expression) => Expression) = NoneInCollection(collection.rewrite(f), symbolName, inner.rewrite(f))
}

case class SingleInCollection(collection: Expression, symbolName: String, inner: Predicate) extends InCollection(collection, symbolName, inner) {
  def seqMethod[U](f: Seq[U]): ((U) => Boolean) => Boolean = x => f.filter(x).length == 1

  def name = "single"

  def rewrite(f: (Expression) => Expression) = SingleInCollection(collection.rewrite(f), symbolName, inner.rewrite(f))
}

object IsCollection extends CollectionSupport {
  def unapply(x: Any):Option[Traversable[Any]] = if (isCollection(x)) {
    Some(castToTraversable(x))
  } else {
    None
  }
}

trait CollectionSupport {
  def isCollection(x: Any) = castToTraversable.isDefinedAt(x)

  def makeTraversable(z: Any): Traversable[Any] = if (castToTraversable.isDefinedAt(z)) {
    castToTraversable(z)
  } else {
    Stream(z)
  }

  def castToTraversable: PartialFunction[Any, Traversable[Any]] = {
    case x: Seq[_] => x
    case x: Array[_] => x
    case x: Map[_, _] => Stream(x)
    case x: JavaMap[_, _] => Stream(x.asScala)
    case x: Iterable[_] => x
    case x: JavaIterable[_] => x.asScala.map {
      case y: JavaMap[_, _] => y.asScala
      case y => y
    }
  }
}