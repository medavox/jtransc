// R : Reflection
var R = function() {
};

function __createField(clazzClazz, info) {
	var out = new java_lang_reflect_Field();
	out["{% FIELD java.lang.reflect.Field:clazz %}"] = clazzClazz;
	out["_internalName"] = info.id;
	out["{% FIELD java.lang.reflect.Field:name %}"] = N.str(info.name);
	out["{% FIELD java.lang.reflect.Field:signature %}"] = N.str(info.desc);
	out["{% FIELD java.lang.reflect.Field:genericSignature %}"] = N.str(info.genericDesc || info.desc);
	out["{% FIELD java.lang.reflect.Field:modifiers %}"] = N.str(info.flags);
	return out;
}

function __createMethod(clazzClazz, info) {
	var out = new java_lang_reflect_Method();
	out["{% FIELD java.lang.reflect.Method:clazz %}"] = clazzClazz;
	out["_internalName"] = info.id;
	out["{% FIELD java.lang.reflect.Method:name %}"] = N.str(info.name);
	out["{% FIELD java.lang.reflect.Method:signature %}"] = N.str(info.desc);
	out["{% FIELD java.lang.reflect.Method:genericSignature %}"] = N.str(info.genericDesc);
	out["{% FIELD java.lang.reflect.Method:modifiers %}"] = N.str(info.flags);
	return out;
}

function __createConstructor(clazzClazz, info) {
	var out = new java_lang_reflect_Constructor();
	out["{% FIELD java.lang.reflect.Constructor:clazz %}"] = clazzClazz;
	out["_internalName"] = info.id;
	out["{% FIELD java.lang.reflect.Constructor:signature %}"] = N.str(info.desc);
	out["{% FIELD java.lang.reflect.Constructor:genericSignature %}"] = N.str(info.genericDesc);
	out["{% FIELD java.lang.reflect.Constructor:modifiers %}"] = N.str(info.flags);
	return out;
}

function __createAnnotation(clazzClazz, info) {
	var out = new java_lang_annotation_Annotation();
	return out;
}

R.__initClass = function(clazzClazz) {
	var clazzName = N.istr(clazzClazz._name);
	var clazz = jtranscClasses[clazzName];

	// Array
	if (clazzName.startsWith('[')) {
		clazzClazz._internalName = clazzName;

		return true;
	} else {
		var clazzInfo = clazz.$$JS_TYPE_CONTEXT$$;

    	clazzClazz._internalName = clazzInfo.internalName;

		if (!clazzInfo.__initClassOnce) {
			clazzInfo.__initClassOnce = true;

			//console.log('__initClass:' + clazzClazz._name);
			//console.log(clazzInfo.fields);

			clazzClazz._fields = clazzInfo.fields.map(function(info) { return __createField(clazzClazz, info); });
			clazzClazz._methods = clazzInfo.methods.map(function(info) { return __createMethod(clazzClazz, info); });;
			clazzClazz._constructors = clazzInfo.constructors.map(function(info) { return __createConstructor(clazzClazz, info); });
			clazzClazz._annotations = clazzInfo.annotations.map(function(info) { return __createAnnotation(clazzClazz, info); });

			clazzClazz._jsClass = clazz;
			clazzClazz._interfaces = clazzInfo.interfaces;
			clazzClazz._superclass = clazzInfo.parent;
			clazzClazz.{% FIELD java.lang.Class:modifiers %} = clazzInfo.flags;

			//java.lang.reflect.Field
		}

		return true;
	}
};

R.getClass = function(obj) {
	if (obj instanceof JA_0) {
		return N.resolveClass(obj.desc);
	}

	var typeContext = obj.$$JS_TYPE_CONTEXT$$;
	if (!typeContext.clazzClazz) {
		typeContext.clazzClazz = N.resolveClass(typeContext.name);
	}
	return typeContext.clazzClazz;
};

R.getField = function(field, obj) {
	var obj2 = (obj == null) ? jtranscClasses[N.istr(field._clazz._name)] : obj;
	return obj2[field._internalName];
};

R.setField = function(field, obj, value) {
	var obj2 = (obj == null) ? jtranscClasses[N.istr(field._clazz._name)] : obj;
	obj2[field._internalName] = value;
};

R.invokeMethod = function(method, obj, args) {
	var obj2 = (obj == null) ? jtranscClasses[N.istr(method._clazz._name)] : obj;
	var result = obj2[method._internalName].apply(obj2, args.data);
	return N.boxWithType(result, method['{% METHOD java.lang.reflect.Method:getReturnType %}']());
};

R.newInstance = function(constructor, args) {
	//console.log(constructor);
	var clazz = constructor._clazz._jsClass;
	var obj = new clazz();
	return obj[constructor._internalName].apply(obj, args.data);
};

R.newProxyInstance = function(ifc, invocationHandler) {
	var javaClass   = N.resolveClass(N.istr(ifc._name));
	var typeContext = jtranscTypeContext[N.istr(ifc._name)];

	if (!typeContext.proxyClass) {
		typeContext.proxyClass = function(__invocationHandler) {
			this.__invocationHandler = __invocationHandler;
		};

		typeContext.proxyClass.prototype = $extend(typeContext.clazz.prototype, {});

		javaClass._methods.forEach(function(method) {
			typeContext.proxyClass.prototype[method._internalName] = function() {
				return N.box(invocationHandler["{% METHOD java.lang.reflect.InvocationHandler:invoke %}"].call(
					invocationHandler,
					this,
					method,
					N.boxArray(Array.from(arguments))
				));
			};
			//console.log(it.id);
		});
		//console.log(typeContext.methods);
		//console.log(typeContext._methods);
	}
	//throw 'WIP';
	return new typeContext.proxyClass(invocationHandler);
};