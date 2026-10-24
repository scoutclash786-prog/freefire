package com.clash.app

data class SupportTicket(
    var id: String = "",
    var uid: String = "",
    var username: String = "",
    var subject: String = "",
    var category: String = "Other",
    var status: String = "open",
    var createdAt: Long = 0L,
    var updatedAt: Long = 0L,
    var lastMessage: String = "",
    var messages: List<SupportTicketMessage> = emptyList()
)

data class SupportTicketMessage(
    var id: String = "",
    var senderUid: String = "",
    var senderName: String = "",
    var senderRole: String = "user",
    var body: String = "",
    var createdAt: Long = 0L
)
