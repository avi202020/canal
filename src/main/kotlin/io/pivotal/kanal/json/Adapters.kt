/*
 * Copyright 2018 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.pivotal.kanal.json

import com.squareup.moshi.*
import io.pivotal.kanal.model.*
import java.lang.reflect.Type

class VariableAdapter {

    data class OrcaVariable(
            val name: String,
            val description: String,
            val type: String,
            val defaultValue: Any?,
            val example: String? = null,
            val nullable: Boolean = false,
            val merge: Boolean = false,
            val remove: Boolean = false
    )

    @ToJson
    fun toJson(variable: Variable): OrcaVariable {
        return OrcaVariable(
                variable.name,
                variable.description,
                variable.typeAttrs.type,
                variable.typeAttrs.defaultValue,
                variable.example,
                variable.nullable,
                variable.merge,
                variable.remove
        )
    }

    @FromJson
    fun fromJson(orcaVariable: OrcaVariable): Variable? {
        val typeAttrs = when(orcaVariable.type) {
            "int" -> if (orcaVariable.defaultValue is Float) {
                IntegerType((orcaVariable.defaultValue).toInt())
            } else {
                IntegerType(orcaVariable.defaultValue as Int)
            }
            "float" -> FloatType(orcaVariable.defaultValue as Float)
            "string" -> StringType(orcaVariable.defaultValue as String)
            "boolean" -> BooleanType(orcaVariable.defaultValue as Boolean)
            "list" -> ListType(orcaVariable.defaultValue as List<Any>)
            else -> ObjectType(orcaVariable.defaultValue)
        }
        return Variable(
                orcaVariable.name,
                orcaVariable.description,
                typeAttrs,
                orcaVariable.example,
                orcaVariable.nullable,
                orcaVariable.merge,
                orcaVariable.remove
        )
    }
}

class PipelineTemplateInstanceAdapter {

    val pipelineConfigurationAdapter by lazy {
        JsonAdapterFactory().createAdapter<PipelineConfiguration>()
    }
    val pipelineAdapter by lazy {
        JsonAdapterFactory().createAdapter<Pipeline>()
    }

    @ToJson
    fun toJson(writer: JsonWriter, pipelineTemplateInstance: PipelineTemplateInstance) {
        writer.beginObject()
        val token = writer.beginFlatten()
        pipelineConfigurationAdapter.toJson(writer, pipelineTemplateInstance.config)
        pipelineAdapter.toJson(writer, pipelineTemplateInstance.pipeline)
        writer.endFlatten(token)
        writer.endObject()
    }

    @FromJson
    fun fromJson(pipelineTemplateInstance: Map<String, @JvmSuppressWildcards Any>): PipelineTemplateInstance {
        return PipelineTemplateInstance(
                pipelineConfigurationAdapter.fromJsonValue(pipelineTemplateInstance)!!,
                pipelineAdapter.fromJsonValue(pipelineTemplateInstance)!!
        )
    }

}

class StageGraphAdapter {
    data class StageExecution(
            val refId: String,
            val requisiteStageRefIds: List<String>,
            val inject: Inject? = null
    )

    val stageAdapter by lazy {
        JsonAdapterFactory().createAdapter<Stage>()
    }
    val executionDetailsAdapter by lazy {
        JsonAdapterFactory().createAdapter<StageExecution>()
    }

    @ToJson
    fun toJson(writer: JsonWriter, stageGraph: StageGraph) {
        writer.beginArray()
        stageGraph.stages.forEach {
            val stageRequirements = stageGraph.stageRequirements[it.refId].orEmpty().map{ it }
            val execution = StageExecution(it.refId, stageRequirements, it.inject)
            writer.beginObject()
            val token = writer.beginFlatten()
            executionDetailsAdapter.toJson(writer, execution)
            stageAdapter.toJson(writer, it.stage)
            writer.endFlatten(token)
            writer.endObject()
        }
        writer.endArray()
    }

    @FromJson
    fun fromJson(stageMaps: List<Map<String, @JvmSuppressWildcards Any>>): StageGraph {
        var stages: List<PipelineStage> = emptyList()
        var stageRequirements: Map<String, List<String>> = mapOf()
        stageMaps.map {
            val stage = stageAdapter.fromJsonValue(it)!!
            val execution = executionDetailsAdapter.fromJsonValue(it)!!
            val refId = execution.refId
            stages += PipelineStage(refId, stage, execution.inject)
            if (execution.requisiteStageRefIds.isNotEmpty()) {
                stageRequirements += (refId to execution.requisiteStageRefIds.map { it })
            }
        }
        return StageGraph(stages, stageRequirements)
    }
}

class ExpressionConditionAdapter {
    @FromJson
    fun fromJson(map: Map<String, @JvmSuppressWildcards Any>): ExpressionCondition {
        val expression = map["expression"]
        return when(expression) {
            is Boolean -> ExpressionCondition(expression)
            else -> ExpressionCondition(expression.toString())
        }
    }
}

class ExpressionPreconditionAdapter {
    @FromJson
    fun fromJson(map: Map<String, @JvmSuppressWildcards Any>): ExpressionPrecondition {
        val context = map["context"]
        val expression = (context as Map<String, Any>)["expression"]
        return when(expression) {
            is Boolean -> ExpressionPrecondition(expression.toString())
            else -> ExpressionPrecondition(expression.toString())
        }
    }
}

val jsonNumberAdapter = object : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (type !== Any::class.java) return null

        val delegate = moshi.nextAdapter<Any>(this, Any::class.java, annotations)
        return object : JsonAdapter<Any>() {
            override fun fromJson(reader: JsonReader): Any? {
                return if (reader.peek() !== JsonReader.Token.NUMBER) {
                    delegate.fromJson(reader)
                } else {
                    val s = reader.nextString()
                    try {
                        Integer.parseInt(s)
                    } catch (e: NumberFormatException) {
                        s.toFloat()
                    }
                }
            }

            override fun toJson(writer: JsonWriter, value: Any?) {
                delegate.toJson(writer, value)
            }
        }
    }
}

