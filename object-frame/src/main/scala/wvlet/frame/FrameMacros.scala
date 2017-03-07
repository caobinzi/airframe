/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package wvlet.frame

import scala.language.experimental.macros
import scala.reflect.macros.{blackbox => sm}

/**
  *
  */
object FrameMacros {

  class Helper[C <: sm.Context](val c: C) {
    import c.universe._
    val seen = scala.collection.mutable.Set[Type]()
    val memo = scala.collection.mutable.Map[Type, c.Tree]()

    def toFrame(t:Type) : c.Tree = {
      if(seen.contains(t)) {
        if(memo.contains(t)) {
          memo(t)
        }
        else {
          c.abort(c.enclosingPosition, s"recursive type: ${t.typeSymbol.fullName}")
        }
      }
      else {
        seen += t
        val frame = t match {
          case tr @ TypeRef(prefix, symbol, args) =>
            val symbolname = tr.typeSymbol.fullName
            //println(s"symbol name: ${symbolname}")
            symbolname match {
              case "scala.Int" => q"wvlet.frame.IntFrame"
              case "scala.Short" => q"wvlet.frame.ShortFrame"
              case "scala.Byte" => q"wvlet.frame.ByteFrame"
              case "scala.Long" => q"wvlet.frame.LongFrame"
              case "scala.Float" => q"wvlet.frame.FloatFrame"
              case "scala.Double" => q"wvlet.frame.DoubleFrame"
              case "scala.Boolean" => q"wvlet.frame.BooleanFrame"
              case "java.lang.String" => q"wvlet.frame.StringFrame"
              case _ =>
                t.members.find(x => x.isMethod && x.asMethod.isPrimaryConstructor) match {
                  case None =>
                    println(s"No primary constructor is found for ${t}")
                    q"new wvlet.frame.ObjectFrame(classOf[$t])"
                  case Some(primaryConstructor) =>
                    val classTypeParams = t.typeSymbol.asClass.typeParams
                    val params = primaryConstructor.asMethod.paramLists.flatten
                    val concreteArgTypes = params.map(_.typeSignature.substituteTypes(classTypeParams, args))
                    val frameParams = for((p, t) <- params.zip(concreteArgTypes)) yield {
                      val name = Literal(Constant(p.name.decodedName.toString))
                      val frame = toFrame(t)
                      val expr = q"wvlet.frame.Param($name, ${frame})"
                      //println(s"p: ${showRaw(expr)}")
                      //println(s"t: ${showRaw(t)}")
                      expr
                    }
                    q"""new wvlet.frame.Frame[$t] {
                       def cl : Class[$t] = classOf[$t]
                       override def params = Seq(..$frameParams)
                    }"""
                }
                // TODO Array types
                // TODO complex types
            }
          case other =>
            q"new wvlet.frame.ObjectFrame(classOf[$t])"
        }
        memo += (t -> frame)
        frame
      }
    }

    def genFrame(typeEv:c.Tree) : c.Tree = {
      val t = typeEv.tpe.typeArgs(0)

      val frameGen = t match {
        case TypeRef(_, cls, args) =>
          toFrame(t)
          // TODO Use t.dealias for aliased type
        case other =>
          q"""new wvlet.frame.Frame[$t] { def cl : Class[$t] = classOf[$t] }"""
      }
      q"wvlet.frame.Frame.frameCache.getOrElseUpdate(classOf[$t], $frameGen).asInstanceOf[wvlet.frame.Frame[$t]]"
    }
  }

  def of[A:c.WeakTypeTag](c: sm.Context)(typeEv: c.Tree) : c.Tree = {
    import c.universe._
    new Helper[c.type](c).genFrame(typeEv)
  }
}