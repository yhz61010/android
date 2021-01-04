package com.leovp.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.utils.file.FileUtil
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
        private const val to = "zytase01@leovp.com"
        private const val from = "zytase01@leovp.com"
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_java_mail)
    }

    fun onSendSimpleEmailClick(@Suppress("UNUSED_PARAMETER") view: View) {
        ioScope.launch {
            val properties = Properties()
            properties.setProperty("mail.transport.protocol", "smtp")
            properties.setProperty("mail.smtp.host", "smtp.mxhichina.com")
            properties.setProperty("mail.smtp.port", "25")
            properties.setProperty("mail.smtp.auth", "true")
            properties.setProperty("mail.smtp.timeout", "10_000")

//        properties.setProperty("mail.smtp.port", "465")
//        properties.setProperty("mail.smtp.socketFactory.port", "465")
//        properties.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")

            val session: Session = Session.getDefaultInstance(properties,
                object : javax.mail.Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication("zytase01@leovp.com", "xxx")
                    }
                }
            )

            runCatching {
                val message = MimeMessage(session)
                message.setFrom(InternetAddress(from))
                message.addRecipient(Message.RecipientType.TO, InternetAddress(to))
                message.subject = "Mail Subject"
                message.setText("Mail Text")
                Transport.send(message)
            }.onFailure { it.printStackTrace() }
        }
    }

    fun onSendAttachmentEmailClick(@Suppress("UNUSED_PARAMETER") view: View) {
        ioScope.launch {
            val attachment = FileUtil.createFile(this@JavaMailActivity, "music.mp3")
            FileUtil.copyInputStreamToFile(resources.openRawResource(R.raw.music), attachment.absolutePath)

            val properties = Properties()
            properties.setProperty("mail.transport.protocol", "smtp")
            properties.setProperty("mail.smtp.host", "smtp.mxhichina.com")
            properties.setProperty("mail.smtp.port", "25")
            properties.setProperty("mail.smtp.auth", "true")
            properties.setProperty("mail.smtp.timeout", "10_000")
            properties["mail.debug"] = true

            val session: Session = Session.getDefaultInstance(properties,
                object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication("zytase01@leovp.com", "xxx")
                    }
                }
            )
            val text = MimeBodyPart()
            text.setContent("<h1>Welcome Leo</h1>", "text/html;charset=UTF-8")

            val attach = MimeBodyPart()
            val dh = DataHandler(FileDataSource(attachment))
            attach.dataHandler = dh
            attach.fileName = dh.name

            val message = MimeMessage(session)

            val address = InternetAddress(from)
            message.setFrom(address)
            message.addRecipient(Message.RecipientType.CC, address)
            message.addRecipient(Message.RecipientType.TO, InternetAddress(to))

            val mp = MimeMultipart()
            mp.addBodyPart(text)
            mp.addBodyPart(attach)
            mp.setSubType("mixed")
            message.subject = "Mail Subject"
            message.setContent(mp)
            message.saveChanges()
            val transport: Transport = session.transport
            transport.connect()
            transport.sendMessage(message, message.allRecipients)
            transport.close()
        }
    }
}