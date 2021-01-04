package com.leovp.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.kotlin.ITAG
import com.leovp.androidbase.utils.file.FileUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
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


class JavaMailActivity : BaseDemonstrationActivity() {
    companion object {
        private const val TO = "zytase01@leovp.com"
        private const val FROM = "zytase01@leovp.com"
        private const val FROM_PWD = "xxx"
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_java_mail)
    }

    // smtp port 25, ssl port 465
    // pop3 port 110, ssl port 995
    // imap port 143, ssl port 993
    private fun getServerProperties(protocol: String, port: Int): Properties {
        return Properties().apply {
            setProperty("mail.transport.protocol", protocol)
            setProperty("mail.$protocol.host", "$protocol.mxhichina.com")
            setProperty("mail.$protocol.port", port.toString())
            setProperty("mail.$protocol.auth", "true")
//            setProperty("mail.$protocol.timeout", "10_000")
//            setProperty("mail.debug", "true")

//        setProperty("mail.smtp.port", "465")
//        setProperty("mail.smtp.socketFactory.port", "465")
//        setProperty("mail.smtp.socketFactory.fallback", "false")
//        setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
        }
    }

    private fun getSession(protocol: String, port: Int, userName: String? = null, pwd: String? = null): Session {
        return Session.getInstance(getServerProperties(protocol, port), if (userName.isNullOrBlank()) null else
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
            val session: Session = getSession("smtp", 25, FROM, FROM_PWD)
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
            val attachment = FileUtil.createFile(this@JavaMailActivity, "music.mp3")
            FileUtil.copyInputStreamToFile(resources.openRawResource(R.raw.music), attachment.absolutePath)
            val session: Session = getSession("smtp", 25, FROM, FROM_PWD)

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
                val session: Session = getSession("pop3", 110)
                session.debug = true

                // connects to the message store
                val store: Store = session.getStore("pop3")
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
                    if (contentType.contains("text/plain") || contentType.contains("text/html")) {
                        try {
                            val content = msg.content
                            if (content != null) {
                                messageContent = content.toString()
                            }
                        } catch (ex: Exception) {
                            messageContent = "[Error downloading content]"
                            ex.printStackTrace()
                        }
                    }

                    // print out details of each message
                    LogContext.log.i(ITAG, "Message #" + (i + 1) + ":")
                    LogContext.log.i(ITAG, "From: $from")
                    LogContext.log.i(ITAG, "To: $toList")
//                    LogContext.log.i(ITAG, "CC: $ccList")
                    LogContext.log.i(ITAG, "Subject: $subject")
                    LogContext.log.i(ITAG, "Sent Date: $sentDate")
                    LogContext.log.i(ITAG, "Message: $messageContent")
                    LogContext.log.i(ITAG, "----------------------------------------------------------------------------------------------------------------")
                }

                // disconnect
                folderInbox.close(false)
                store.close()
            }.onFailure { it.printStackTrace() }
            LogContext.log.i(ITAG, "Received mails.")
        }
    }
}