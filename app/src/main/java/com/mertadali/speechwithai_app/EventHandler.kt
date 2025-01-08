package com.mertadali.speechwithai_app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.google.gson.Gson
import com.mertadali.speechwithai_app.repository.FirebaseRepository

class EventHandler(
    private val chatGPTService: ChatGPTService,
    private val firebaseRepository: FirebaseRepository
) {
    private val _responseFlow = MutableStateFlow<String>("")
    val responseFlow: StateFlow<String> = _responseFlow

    suspend fun handleEvent(event: AssistantEvent) {
        try {
            when (event.event) {
                "thread.run.requires_action" -> {
                    handleRequiresAction(event.data)
                }
                "thread.message.delta" -> {
                    event.data.delta?.content?.firstOrNull()?.text?.value?.let { value ->
                        _responseFlow.emit(_responseFlow.value + value)
                    }
                }
                "thread.run.completed" -> {
                    // İşlem tamamlandı
                }
            }
        } catch (e: Exception) {
            _responseFlow.emit("Hata: ${e.message}")
        }
    }

    private suspend fun handleRequiresAction(data: EventData) {
        try {
            val toolOutputs = mutableListOf<ToolOutput>()

            data.required_action?.submit_tool_outputs?.tool_calls?.forEach { toolCall ->
                when (toolCall.function.name) {
                    "get_stock_info" -> {
                        val arguments = toolCall.function.arguments
                        val parsedArgs = Gson().fromJson(arguments, StockQueryArgs::class.java)
                        val stockInfo = firebaseRepository.getStockInfo(parsedArgs.product_name)

                        toolOutputs.add(
                            ToolOutput(
                                tool_call_id = toolCall.id,
                                output = Gson().toJson(stockInfo)
                            )
                        )
                    }
                }
            }

            if (toolOutputs.isNotEmpty()) {
                submitToolOutputs(data.thread_id, data.id, toolOutputs)
            }
        } catch (e: Exception) {
            _responseFlow.emit("Hata: ${e.message}")
        }
    }

    private suspend fun submitToolOutputs(threadId: String, runId: String, toolOutputs: List<ToolOutput>) {
        chatGPTService.submitToolOutputs(threadId, runId, ToolOutputsRequest(toolOutputs))
    }
}

data class AssistantEvent(
    val event: String,
    val data: EventData
)

data class EventData(
    val id: String,
    val thread_id: String,
    val required_action: RequiredAction? = null,
    val delta: Delta? = null
)

data class RequiredAction(
    val submit_tool_outputs: SubmitToolOutputs
)

data class SubmitToolOutputs(
    val tool_calls: List<ToolCall>
)

data class ToolCall(
    val id: String,
    val function: FunctionCall
)

data class FunctionCall(
    val name: String,
    val arguments: String
)

data class Delta(
    val content: List<MessageContent>?
)

data class ToolOutput(
    val tool_call_id: String,
    val output: String
)

data class ToolOutputsRequest(
    val tool_outputs: List<ToolOutput>
)

data class StockQueryArgs(
    val product_name: String
)