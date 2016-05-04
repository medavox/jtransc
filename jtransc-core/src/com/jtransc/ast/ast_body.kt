package com.jtransc.ast

import com.jtransc.ds.cast
import com.jtransc.error.invalidOp
import com.jtransc.error.noImpl

data class AstBody(
	val stm: AstStm,
	var locals: List<AstLocal>,
	val traps: List<AstTrap>
)

enum class AstBinop(val symbol: String, val str: String) {
	ADD("+", "add"), SUB("-", "sub"), MUL("*", "mul"), DIV("/", "div"), REM("%", "rem"),
	AND("&", "and"), OR("|", "or"), XOR("^", "xor"),
	SHL("<<", "shl"), SHR(">>", "shr"), USHR(">>>", "ushr"),
	BAND("&&", "band"), BOR("||", "bor"),
	EQ("==", "eq"), NE("!=", "ne"), GE(">=", "ge"), LE("<=", "le"), LT("<", "lt"), GT(">", "gt"),
	LCMP("lcmp", "lcmp"), CMP("cmp", "cmp"), CMPL("cmpl", "cmpl"), CMPG("cmpg", "cmpg");

	companion object {
		val SHIFTS = setOf(SHL, SHR, USHR)
		val COMPARISONS = setOf(EQ, NE, GE, LE, LT, GT)
		//val operators = values.flatMap { listOf(Pair(it.symbol, it), Pair(it.str, it)) }.toMap()
	}
}

enum class AstUnop(val symbol: String, val str: String) {
	NEG("-", "neg"),
	NOT("!", "not"),
	INV("~", "inv");

	companion object {
		//val operators = values.flatMap { listOf(Pair(it.symbol, it), Pair(it.str, it)) }.toMap()
	}
}

data class AstLocal(val index: Int, val name: String, val type: AstType) {
	override fun toString() = "AstLocal:$name:$type(w:$writesCount,r:$readCount)"

	val writes = arrayListOf<AstStm.SET_LOCAL>()
	val reads = arrayListOf<AstExpr.LOCAL>()

	val writesCount: Int get() = writes.size // @TODO: In SSA this should be one
	val readCount: Int get() = reads.size
	val isUsed: Boolean get() = (writesCount != 0) || (readCount != 0)

	fun write(set: AstStm.SET_LOCAL) {
		writes += set
	}

	fun read(ref: AstExpr.LOCAL) {
		reads += ref
	}
}

fun AstType.local(name: String, index: Int = 0) = AstExpr.LOCAL(AstLocal(index, name, this))

data class AstTrap(val start: AstLabel, val end: AstLabel, val handler: AstLabel, val exception: AstType.REF)

data class AstLabel(val name: String) {

}

interface AstElement

interface Cloneable<T> {
	fun clone(): T
}

open class AstStm() : AstElement, Cloneable<AstStm> {
	class Box(_value: AstStm) {
		var value: AstStm = _value
			get() = field
			set(value) {
				field.box = AstStm.Box(field)
				field = value
				field.box = this
			}

		init {
			_value.box = this
		}
	}

	var box: AstStm.Box = AstStm.Box(this)

	override fun clone(): AstStm = noImpl("AstStm.clone: $this")

	class STMS(stms: List<AstStm>) : AstStm() {
		constructor(vararg stms: AstStm) : this(stms.toList())

		val stms = stms.map { it.box }
	}

	class NOP() : AstStm() {

	}

	class LINE(val line: Int) : AstStm() {
		override fun toString() = "AstStm.LINE($line)"
	}

	class STM_EXPR(expr: AstExpr) : AstStm() {
		val expr = expr.box
	}

	class SET_LOCAL(val local: AstExpr.LOCAL, expr: AstExpr) : AstStm() {
		val expr = expr.box
	}

	class SET_ARRAY(array: AstExpr, index: AstExpr, expr: AstExpr) : AstStm() {
		val array = array.box
		val index = index.box
		val expr = expr.box
	}

	class SET_FIELD_STATIC(val field: AstFieldRef, expr: AstExpr) : AstStm() {
		val clazz = AstType.REF(field.classRef.fqname)
		val expr = expr.box
	}

	class SET_FIELD_INSTANCE(val field: AstFieldRef, left: AstExpr, expr: AstExpr) : AstStm() {
		val left = left.box
		val expr = expr.box
	}

	class SET_NEW_WITH_CONSTRUCTOR(val local: AstExpr.LocalExpr, val target: AstType.REF, val method: AstMethodRef, args: List<AstExpr>) : AstStm() {
		val args = args.map { it.box }
	}

	class IF(cond: AstExpr, strue: AstStm) : AstStm() {
		val cond = cond.box
		val strue = strue.box
	}

	class IF_ELSE(cond: AstExpr, strue: AstStm, sfalse: AstStm) : AstStm() {
		val cond = cond.box
		val strue = strue.box
		val sfalse = sfalse.box
	}

	class WHILE(cond: AstExpr, iter: AstStm) : AstStm() {
		val cond = cond.box
		val iter = iter.box
	}

	class RETURN(retval: AstExpr) : AstStm() {
		val retval = retval.box
	}

	class RETURN_VOID() : AstStm() {
	}

	class THROW(value: AstExpr) : AstStm() {
		val value = value.box
	}

	class RETHROW() : AstStm() {

	}

	//data class TRY_CATCH(val trystm: AstStm, val catches: List<Pair<AstType, AstStm>>) : AstStm
	class TRY_CATCH(trystm: AstStm, catch: AstStm) : AstStm() {
		val trystm = trystm.box
		val catch = catch.box
	}

	class BREAK() : AstStm() {

	}

	class CONTINUE() : AstStm() {

	}

	// SwitchFeature
	class SWITCH(subject: AstExpr, default: AstStm, cases: List<Pair<Int, AstStm>>) : AstStm() {
		val subject = subject.box
		val default = default.box
		val cases = cases.map { it.first to it.second.box }
	}

	// GotoFeature

	class STM_LABEL(val label: AstLabel) : AstStm() {

	}

	class SWITCH_GOTO(subject: AstExpr, val default: AstLabel, val cases: List<Pair<Int, AstLabel>>) : AstStm() {
		val subject = subject.box
	}

	class IF_GOTO(val label: AstLabel, cond: AstExpr) : AstStm() {
		val cond = cond.box
	}

	class GOTO(val label: AstLabel) : AstStm() {
	}

	class MONITOR_ENTER(expr: AstExpr) : AstStm() {
		val expr = expr.box
	}

	class MONITOR_EXIT(expr: AstExpr) : AstStm() {
		val expr = expr.box
	}

	//class DEBUG() : AstStm() {
	//}
	//
	//class NOT_IMPLEMENTED() : AstStm() {
	//}

	companion object {
		fun build(build: AstBuilder.() -> AstStm): AstStm = AstBuilder().build()
	}
}

abstract class AstExpr : AstElement, Cloneable<AstExpr> {
	class Box(_value: AstExpr) {
		var value: AstExpr = _value
			get() = field
			set(value) {
				field.box = AstExpr.Box(field)
				field = value
				field.box = this
			}

		init {
			_value.box = this
		}

		val type: AstType get() = value.type
	}

	var box: AstExpr.Box = AstExpr.Box(this)
	var stm: AstStm? = null

	abstract val type: AstType

	override fun clone(): AstExpr = noImpl("AstExpr.clone: $this")

	abstract class ImmutableRef : AstExpr()
	abstract class LValueExpr : AstExpr() {
	}

	abstract class LocalExpr : LValueExpr() {
		abstract val name: String
	}

	// Reference

	class THIS(val ref: FqName) : LocalExpr() {
		override val name: String get() = "this"
		override val type: AstType = AstType.REF(ref)

		override fun clone(): AstExpr.THIS = THIS(ref)
	}

	class LOCAL(val local: AstLocal) : LocalExpr() {
		override val name: String get() = local.name
		override val type = local.type

		override fun clone(): AstExpr.LOCAL = LOCAL(local)
	}

	class PARAM(val argument: AstArgument) : LocalExpr() {
		override val name: String get() = argument.name
		override val type = argument.type

		override fun clone(): AstExpr.PARAM = PARAM(argument)
	}

	abstract class LiteralExpr : AstExpr() {
		abstract val value: Any?
	}

	/*
	class METHODTYPE_CONSTANT(val methodType: AstType.METHOD) : LiteralExpr() {
		override val value = methodType
		override val type: AstType = methodType
	}

	class METHODREF_CONSTANT(val methodRef: AstMethodRef) : LiteralExpr() {
		override val value = methodRef
		override val type: AstType = AstType.UNKNOWN
	}

	class METHODHANDLE_CONSTANT(val methodHandle: AstMethodHandle) : LiteralExpr() {
		override val value = methodHandle
		override val type: AstType = AstType.UNKNOWN
	}
	*/

	class LITERAL(override val value: Any?) : LiteralExpr() {
		override val type = AstType.fromConstant(value)
	}

	class CAUGHT_EXCEPTION(override val type: AstType = AstType.OBJECT) : AstExpr() {

	}

	class BINOP(override val type: AstType, left: AstExpr, val op: AstBinop, right: AstExpr) : AstExpr() {
		val left = left.box
		val right = right.box
	}

	class UNOP(val op: AstUnop, right: AstExpr) : AstExpr() {
		val right = right.box
		override val type = right.type
	}

	abstract class CALL_BASE : AstExpr() {
		//override val type = method.type.ret
		abstract val method: AstMethodRef
		abstract val args: List<AstExpr.Box>
		abstract val isSpecial: Boolean
	}

	class CALL_INSTANCE(obj: AstExpr, override val method: AstMethodRef, args: List<AstExpr>, override val isSpecial: Boolean = false) : CALL_BASE() {
		val obj = obj.box
		override val args = args.map { it.box }

		override val type = method.type.ret
	}

	//class CALL_SPECIAL(obj: AstExpr, override val method: AstMethodRef, args: List<AstExpr>, override val isSpecial: Boolean = false) : CALL_BASE() {
	//	val obj = obj.box
	//	override val args = args.map { it.box }
	//
	//	override val type = method.type.ret
	//}

	class CALL_SUPER(obj: AstExpr, val target: FqName, override val method: AstMethodRef, args: List<AstExpr>, override val isSpecial: Boolean = false) : CALL_BASE() {
		val obj = obj.box
		override val args = args.map { it.box }

		override val type = method.type.ret
	}

	class CALL_STATIC(val clazz: AstType.REF, override val method: AstMethodRef, args: List<AstExpr>, override val isSpecial: Boolean = false) : CALL_BASE() {
		override val args = args.map { it.box }
		//val clazz: AstType.REF = method.classRef.type
		override val type = method.type.ret
	}

	class ARRAY_LENGTH(array: AstExpr) : AstExpr() {
		val array = array.box
		override val type = AstType.INT
	}

	class ARRAY_ACCESS(array: AstExpr, index: AstExpr) : LValueExpr() {
		val array = array.box
		val index = index.box
		override val type = array.type.elementType
	}

	class INSTANCE_FIELD_ACCESS(val field: AstFieldRef, expr: AstExpr) : LValueExpr() {
		val expr = expr.box
		override val type: AstType = field.type
	}

	class STATIC_FIELD_ACCESS(val field: AstFieldRef) : LValueExpr() {
		val clazzName = field.containingTypeRef
		override val type: AstType = field.type
	}

	class INSTANCE_OF(expr: AstExpr, val checkType: AstType) : AstExpr() {
		val expr = expr.box

		override val type = AstType.BOOL
	}

	class CAST(expr: AstExpr, val to: AstType) : AstExpr() {
		val expr = expr.box
		val from: AstType get() = expr.type

		override val type = to

		override fun clone(): AstExpr = CAST(expr.value.clone(), to)
	}

	class NEW(val target: AstType.REF) : AstExpr() {
		override val type = target
	}

	class NEW_WITH_CONSTRUCTOR(val target: AstType.REF, val method: AstMethodRef, args: List<AstExpr>) : AstExpr() {
		val args = args.map { it.box }
		override val type = target
	}

	class NEW_ARRAY(val arrayType: AstType.ARRAY, counts: List<AstExpr>) : AstExpr() {
		val counts = counts.map { it.box }
		override val type = arrayType
	}

	class METHOD_CLASS(
		val methodInInterfaceRef: AstMethodRef,
		val methodToConvertRef: AstMethodRef
	) : AstExpr() {
		override val type = AstType.REF(methodInInterfaceRef.containingClass)
	}

	infix fun ge(that: AstExpr) = AstExpr.BINOP(AstType.BOOL, this, AstBinop.GE, that)
	infix fun le(that: AstExpr) = AstExpr.BINOP(AstType.BOOL, this, AstBinop.LE, that)
	infix fun band(that: AstExpr) = AstExpr.BINOP(AstType.BOOL, this, AstBinop.BAND, that)
	infix fun and(that: AstExpr) = AstExpr.BINOP(this.type, this, AstBinop.AND, that)
	infix fun instanceof(that: AstType) = AstExpr.INSTANCE_OF(this, that)

	companion object {
		fun build(build: AstBuilder.() -> AstExpr): AstExpr = AstBuilder().build()
	}
}

object AstStmUtils {
	fun set(local: AstLocal, value: AstExpr): AstStm.SET_LOCAL {
		val stm = AstStm.SET_LOCAL(AstExpr.LOCAL(local), AstExprUtils.fastcast(value, local.type))
		local.write(stm)
		return stm
	}

	fun stms(stms: List<AstStm>): AstStm = when (stms.size) {
		0 -> AstStm.NOP()
		1 -> stms[0]
		else -> AstStm.STMS(stms)
	}
}

object AstExprUtils {
	fun localRef(local: AstLocal): AstExpr.LOCAL {
		val localExpr = AstExpr.LOCAL(local)
		//val refExpr = AstExpr.REF(localExpr)
		local.read(localExpr)
		return localExpr
	}

	fun cast(expr: AstExpr, to: AstType): AstExpr {
		if (expr is AstExpr.LITERAL) {
			val value = expr.value
			when (value) {
				is Boolean -> castLiteral(value, to)
				is Byte -> castLiteral(value, to)
				is Char -> castLiteral(value, to)
				is Short -> castLiteral(value, to)
				is Int -> castLiteral(value, to)
				is Long -> castLiteral(value, to)
				is Float -> castLiteral(value, to)
				is Double -> castLiteral(value, to)
			}
			//return AstExpr.LITERAL(expr.value)
		}

		if (expr.type != to) {
			return AstExpr.CAST(expr, to)
		} else {
			return expr
		}
	}

	// Can cast nulls
	fun fastcast(expr: AstExpr, to: AstType): AstExpr {
		// LITERAL + IMMEDIATE = IMMEDIATE casted
		if (expr.type != to) {
			return AstExpr.CAST(expr, to)
		} else {
			return expr
		}
	}

	fun INVOKE_DYNAMIC(generatedMethodRef: AstMethodWithoutClassRef, bootstrapMethodRef: AstMethodRef, bootstrapArgs: List<AstExpr>): AstExpr {
		if (bootstrapMethodRef.containingClass.fqname == "java.lang.invoke.LambdaMetafactory" &&
			bootstrapMethodRef.name == "metafactory"
		) {
			val literals = bootstrapArgs.cast<AstExpr.LiteralExpr>()
			val interfaceMethodType = literals[0].value as AstType.METHOD
			val methodHandle = literals[1].value as AstMethodHandle
			val methodType = literals[2].type

			val interfaceToGenerate = generatedMethodRef.type.ret as AstType.REF
			val methodToConvertRef = methodHandle.methodRef

			return AstExpr.METHOD_CLASS(
				AstMethodRef(interfaceToGenerate.name, generatedMethodRef.name, interfaceMethodType),
				methodToConvertRef
			)
		} else {
			noImpl("Not supported DynamicInvoke yet!")
		}
	}

	fun INVOKE_SPECIAL(obj: AstExpr, method: AstMethodRef, args: List<AstExpr>): AstExpr.CALL_BASE {
		if (obj.type !is AstType.REF) {
			invalidOp("Obj must be an object $obj, but was ${obj.type}")
		}

		//if (obj is AstExpr.THIS && ((obj.type as AstType.REF).name != method.containingClass)) {
		if ((obj.type as AstType.REF).name != method.containingClass) {
			//if (caller == "<init>" && ((obj.type as AstType.REF).name != method.containingClass)) {
			return AstExpr.CALL_SUPER(cast(obj, method.containingClassType), method.containingClass, method, args, isSpecial = true)
		} else {
			return AstExpr.CALL_INSTANCE(cast(obj, method.containingClassType), method, args, isSpecial = true)
		}
	}

	fun BINOP(type: AstType, l: AstExpr, op: AstBinop, r: AstExpr): AstExpr.BINOP {
		if (l.type == AstType.BOOL && r.type == AstType.BOOL) {
			if (op == AstBinop.AND) return AstExpr.BINOP(AstType.BOOL, cast(l, AstType.BOOL), AstBinop.BAND, cast(r, AstType.BOOL))
			if (op == AstBinop.OR) return AstExpr.BINOP(AstType.BOOL, cast(l, AstType.BOOL), AstBinop.BOR, cast(r, AstType.BOOL))
			if (op == AstBinop.XOR) return AstExpr.BINOP(AstType.BOOL, cast(l, AstType.BOOL), AstBinop.NE, cast(r, AstType.BOOL))
		} else if (l.type == AstType.BOOL) {
			return AstExpr.BINOP(type, cast(l, r.type), op, r)
		} else if (r.type == AstType.BOOL) {
			return AstExpr.BINOP(type, l, op, cast(r, l.type))
		}
		return AstExpr.BINOP(type, l, op, r)
	}

	fun RESOLVE_SPECIAL(program: AstProgram, e: AstExpr.CALL_INSTANCE, context: AstGenContext): AstExpr.CALL_BASE {
		val clazz = program[e.method.classRef]
		val refMethod = program.get(e.method) ?: invalidOp("Can't find method: ${e.method} while generating $context")
		// https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.invokespecial
		return if (refMethod.modifiers.isPrivate || refMethod.isInstanceInit) {
			// Call this!
			AstExpr.CALL_INSTANCE(e.obj.value, e.method, e.args.map { it.value }, e.isSpecial)
		} else {
			// Call super!
			if (context.method.ref != e.method) {
				AstExpr.CALL_SUPER(e.obj.value, e.method.containingClass, e.method, e.args.map { it.value }, e.isSpecial)
			} else {
				AstExpr.CALL_INSTANCE(e.obj.value, e.method, e.args.map { it.value }, e.isSpecial)
			}
		}
	}
}

operator fun AstExpr.plus(that: AstExpr) = AstExpr.BINOP(this.type, this, AstBinop.ADD, that)
operator fun AstExpr.minus(that: AstExpr) = AstExpr.BINOP(this.type, this, AstBinop.SUB, that)

class AstBuilder {
	val BOOL = AstType.BOOL
	val BYTE = AstType.BYTE
	val SHORT = AstType.SHORT
	val CHAR = AstType.CHAR
	val INT = AstType.INT
	val LONG = AstType.LONG
	val FLOAT = AstType.FLOAT
	val DOUBLE = AstType.DOUBLE
	val OBJECT = AstType.OBJECT
	val CLASS = AstType.CLASS
	val STRING = AstType.STRING

	fun AstExpr.cast(type: AstType) = AstExpr.CAST(this, type)
	val Any?.lit: AstExpr.LITERAL get() = AstExpr.LITERAL(this)

	operator fun AstExpr.plus(that: AstExpr) = AstExpr.BINOP(this.type, this, AstBinop.ADD, that)
	operator fun AstExpr.minus(that: AstExpr) = AstExpr.BINOP(this.type, this, AstBinop.SUB, that)
	operator fun AstExpr.times(that: AstExpr) = AstExpr.BINOP(this.type, this, AstBinop.MUL, that)
	infix fun AstExpr.eq(that: AstExpr) = AstExpr.BINOP(this.type, this, AstBinop.EQ, that)
	infix fun AstExpr.ne(that: AstExpr) = AstExpr.BINOP(this.type, this, AstBinop.NE, that)
	fun AstExpr.stm() = AstStm.STM_EXPR(this)
	infix fun AstLocal.assignTo(that: AstExpr) = AstStm.SET_LOCAL(AstExpr.LOCAL(this), that)
	infix fun AstExpr.LOCAL.assignTo(that: AstExpr) = AstStm.SET_LOCAL(this, that)
	//fun FqName.get(name:String):AstExpr.STATIC_FIELD_ACCESS = AstExpr.STATIC_FIELD_ACCESS(AstFieldRef())
}

fun AstBuild(build: AstBuilder.() -> AstExpr): AstExpr {
	return AstBuilder().build()
}

fun AstBuildStm(build: AstBuilder.() -> AstStm): AstStm {
	return AstBuilder().build()
}

fun AstExpr.builder() {

}

class AstMethodHandle(val type: AstType.METHOD, val methodRef: AstMethodRef, val kind: Kind) {
	enum class Kind(val id: Int) {
		REF_getField(1),
		REF_getStatic(2),
		REF_putField(3),
		REF_putStatic(4),
		REF_invokeVirtual(5),
		REF_invokeStatic(6),
		REF_invokeSpecial(7),
		REF_newInvokeSpecial(8),
		REF_invokeInterface(9);

		companion object {
			private val table = values().map { it.id to it }.toMap()
			fun fromId(id: Int) = table[id]!!
		}
	}

}

val Iterable<AstStm>.stms: AstStm get() = AstStm.STMS(this.toList())
val Any?.lit: AstExpr get() = AstExpr.LITERAL(this)
