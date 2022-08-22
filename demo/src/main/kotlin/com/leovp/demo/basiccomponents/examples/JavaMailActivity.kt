package com.leovp.demo.basiccomponents.examples

import android.os.Bundle
import android.view.View
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityJavaMailBinding
import com.leovp.android.exts.createFile
import com.leovp.android.exts.toFile
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class JavaMailActivity : BaseDemonstrationActivity<ActivityJavaMailBinding>() {
    override fun getTagName(): String = ITAG

    companion object {
        private const val TO = "t@leovp.com"
        private const val FROM = "t@leovp.com"
        private const val FROM_PWD = "Temp123456:)"

        private const val EMAIL_POP_PROTOCOL = "pop3"
        private const val EMAIL_POP_HOST = "pop.qiye.aliyun.com"
        private const val EMAIL_POP_PORT = 995
        private const val EMAIL_POP_SSL = true

        private const val EMAIL_SMTP_PROTOCOL = "smtp"
        private const val EMAIL_SMTP_HOST = "smtp.qiye.aliyun.com"
        private const val EMAIL_SMTP_PORT = 465
        private const val EMAIL_SMTP_SSL = true
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityJavaMailBinding {
        return ActivityJavaMailBinding.inflate(layoutInflater)
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    // smtp port 25, ssl port 465
    // pop3 port 110, ssl port 995
    // imap port 143, ssl port 993
    private fun getServerProperties(
        protocol: String,
        mailHost: String,
        port: Int,
        enableSsl: Boolean = false
    ): Properties {
        return Properties().apply {
            setProperty("mail.transport.protocol", protocol)
            setProperty("mail.$protocol.host", mailHost)
            setProperty("mail.$protocol.port", port.toString())
            setProperty("mail.$protocol.auth", "true")
            setProperty("mail.$protocol.ssl.enable", enableSsl.toString())
//            setProperty("mail.$protocol.timeout", "10_000")
//            setProperty("mail.debug", "true")

//        setProperty("mail.smtp.port", "465")
//        setProperty("mail.smtp.socketFactory.port", "465")
//        setProperty("mail.smtp.socketFactory.fallback", "false")
//        setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
        }
    }

    private fun getSession(
        protocol: String,
        mailHost: String,
        port: Int,
        userName: String? = null,
        pwd: String? = null,
        enableSsl: Boolean = false
    ): Session {
        return Session.getInstance(
            getServerProperties(protocol, mailHost, port, enableSsl),
            if (userName.isNullOrBlank()) null else
                object : javax.mail.Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(userName, pwd)
                    }
                }
        )
    }

    /**
     * Returns a list of addresses in String format separated by comma
     *
     * @param address an array of Address objects
     * @return a string represents a list of addresses
     */
    private fun parseAddresses(address: Array<Address>): String = address.joinToString(",")

    fun onSendSimpleEmailClick(@Suppress("UNUSED_PARAMETER") view: View) {
        ioScope.launch {
            LogContext.log.i(ITAG, "Sending simple mail...")
            val session: Session = getSession(EMAIL_SMTP_PROTOCOL, EMAIL_SMTP_HOST, EMAIL_SMTP_PORT, FROM, FROM_PWD, EMAIL_SMTP_SSL)
            runCatching {
                val message = MimeMessage(session)
                message.setFrom(InternetAddress(FROM))
                message.addRecipient(Message.RecipientType.TO, InternetAddress(TO))
                message.subject = "Mail Subject - ${System.currentTimeMillis()}"
                message.setText("Mail Text")
                Transport.send(message)
            }.onFailure { it.printStackTrace() }

            LogContext.log.i(ITAG, "Simple mail sent.")
        }
    }

    fun onSendAttachmentEmailClick(@Suppress("UNUSED_PARAMETER") view: View) {
        ioScope.launch {
            LogContext.log.i(ITAG, "Sending attachment mail...")
            val attachment = this@JavaMailActivity.createFile("music.mp3")
            resources.openRawResource(R.raw.music).toFile(attachment.absolutePath)
            val session: Session = getSession(EMAIL_SMTP_PROTOCOL, EMAIL_SMTP_HOST, EMAIL_SMTP_PORT, FROM, FROM_PWD, EMAIL_SMTP_SSL)

            val text = MimeBodyPart()
            text.setContent("<h1>Welcome Leo</h1>", "text/html;charset=UTF-8")

            val attach = MimeBodyPart()
            val dh = DataHandler(FileDataSource(attachment))
            attach.dataHandler = dh
            attach.fileName = dh.name

            val message = MimeMessage(session)
            val address = InternetAddress(FROM)
            message.setFrom(address)
//            message.addRecipient(Message.RecipientType.CC, address)
            message.addRecipient(Message.RecipientType.TO, InternetAddress(TO))

            val mp = MimeMultipart()
            mp.addBodyPart(text)
            mp.addBodyPart(attach)
            mp.setSubType("mixed")
            message.subject = "Mail Subject Attachment - ${System.currentTimeMillis()}"
            message.setContent(mp)
            message.saveChanges()
            val transport: Transport = session.transport
            transport.connect()
            transport.sendMessage(message, message.allRecipients)
            transport.close()

            LogContext.log.i(ITAG, "Attachment mail sent.")
        }
    }

    fun onReceiveMailClick(@Suppress("UNUSED_PARAMETER") view: View) {
        ioScope.launch {
            LogContext.log.i(ITAG, "Receiving mails...")
            runCatching {
                val session: Session = getSession(EMAIL_POP_PROTOCOL, EMAIL_POP_HOST, EMAIL_POP_PORT, enableSsl = EMAIL_POP_SSL)
                session.debug = true

                // connects to the message store
                val store: Store = session.getStore(EMAIL_POP_PROTOCOL)
                store.connect(FROM, FROM_PWD)
//                store.connect()

                // opens the inbox folder
                val folderInbox = store.getFolder("INBOX")
                folderInbox.open(Folder.READ_ONLY)

                // fetches new messages from server
                val messages = folderInbox.messages
                messages.sortByDescending { it.sentDate }

                for (i in messages.indices) {
                    val msg = messages[i]
                    val fromAddress = msg.from
                    val from = fromAddress[0].toString()
                    val subject = msg.subject
                    val toList: String = parseAddresses(msg.getRecipients(Message.RecipientType.TO))
//                    val ccList: String = parseAddresses(msg.getRecipients(Message.RecipientType.CC))
                    val sentDate = msg.sentDate.toString()
                    val contentType = msg.contentType
                    var messageContent = ""
                    var messageHtmlContent = ""
                    if (contentType.contains("text/plain") || contentType.contains("text/html")) {
                        runCatching {
                            messageContent = msg.content?.toString() ?: ""
                        }.onFailure {
                            messageContent = "[Error downloading content]"
                            it.printStackTrace()
                        }
                    } else if (contentType.contains("multipart")) {
                        val multiPart = msg.content as? Multipart
                        val numberOfParts = multiPart?.count ?: 0
                        for (partCount in 0 until numberOfParts) {
                            val part: MimeBodyPart? = multiPart?.getBodyPart(partCount) as? MimeBodyPart
                            LogContext.log.i(ITAG, "    Part[$partCount] content type: ${part?.contentType}")
                            if (part?.contentType?.contains("text/plain") == true) {
                                messageContent += part.content.toString()
                            } else if (part?.contentType?.contains("text/html") == true) {
                                // TODO remove useless html tag?
                                messageHtmlContent += part.content.toString()
                            }
                        }
                    }
                    if (messageContent.isBlank()) messageContent = messageHtmlContent

                    // print out details of each message
                    LogContext.log.i(
                        ITAG,
                        "Message(${contentType.substring(0, if (contentType.length > 15) 15 else contentType.length)}) #" + (i + 1) + ":"
                    )
                    LogContext.log.i(ITAG, "From: $from")
                    LogContext.log.i(ITAG, "To: $toList")
//                    LogContext.log.i(ITAG, "CC: $ccList")
                    LogContext.log.i(ITAG, "Subject: $subject")
                    LogContext.log.i(ITAG, "Sent Date: $sentDate")
                    LogContext.log.i(ITAG, "Message: $messageContent")
                    LogContext.log.i(ITAG, "------------------------------------------------------------------------------------------------")
                }

                // disconnect
                folderInbox.close(false)
                store.close()
            }.onFailure { it.printStackTrace() }
            LogContext.log.i(ITAG, "Received mails.")
        }
    }
}
