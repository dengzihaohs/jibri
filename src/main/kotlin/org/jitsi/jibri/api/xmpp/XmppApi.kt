/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.jitsi.jibri.api.xmpp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.minio.PutObjectArgs
import org.jitsi.jibri.FileRecordingRequestParams
import org.jitsi.jibri.JibriBusyException
import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.MainConfig
import org.jitsi.jibri.config.Config
import org.jitsi.jibri.config.MinioClientConfig
import org.jitsi.jibri.config.XmppEnvironmentConfig
import org.jitsi.jibri.extensions.JibriNodeExtension
import org.jitsi.jibri.extensions.RecordExtendIq
import org.jitsi.jibri.extensions.RecordingIq
import org.jitsi.jibri.health.EnvironmentContext
import org.jitsi.jibri.selenium.CallParams
import org.jitsi.jibri.service.AppData
import org.jitsi.jibri.service.JibriServiceStatusHandler
import org.jitsi.jibri.service.ServiceParams
import org.jitsi.jibri.service.impl.SipGatewayServiceParams
import org.jitsi.jibri.service.impl.StreamingParams
import org.jitsi.jibri.service.impl.YOUTUBE_URL
import org.jitsi.jibri.sipgateway.SipClientParams
import org.jitsi.jibri.statsd.JibriStatsDClient
import org.jitsi.jibri.statsd.STOPPED_ON_XMPP_CLOSED
import org.jitsi.jibri.statsd.XMPP_CLOSED
import org.jitsi.jibri.statsd.XMPP_CLOSED_ON_ERROR
import org.jitsi.jibri.statsd.XMPP_CONNECTED
import org.jitsi.jibri.statsd.XMPP_PING_FAILED
import org.jitsi.jibri.statsd.XMPP_RECONNECTING
import org.jitsi.jibri.statsd.XMPP_RECONNECTION_FAILED
import org.jitsi.jibri.status.ComponentState
import org.jitsi.jibri.status.JibriStatus
import org.jitsi.jibri.status.JibriStatusManager
import org.jitsi.jibri.util.TaskPools
import org.jitsi.jibri.util.getCallUrlInfoFromJid
import org.jitsi.metaconfig.config
import org.jitsi.utils.logging2.createLogger
import org.jitsi.xmpp.extensions.jibri.JibriIq
import org.jitsi.xmpp.extensions.jibri.JibriIqProvider
import org.jitsi.xmpp.extensions.jibri.JibriStatusPacketExt
import org.jitsi.xmpp.mucclient.ConnectionStateListener
import org.jitsi.xmpp.mucclient.IQListener
import org.jitsi.xmpp.mucclient.MucClient
import org.jitsi.xmpp.mucclient.MucClientConfiguration
import org.jitsi.xmpp.mucclient.MucClientManager
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.StanzaError
import org.jivesoftware.smack.provider.ProviderManager
import org.jivesoftware.smackx.ping.PingManager
import org.jxmpp.jid.impl.JidCreate
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.util.*
import kotlin.io.path.Path

private class UnsupportedIqMode(val iqMode: String) : Exception()

/**
 * [XmppApi] connects to XMPP MUCs according to the given [XmppEnvironmentConfig]s (which are
 * parsed from config.json) and listens for IQ messages which contain Jibri commands, which it relays
 * to the given [JibriManager].  The IQ messages are instances of [JibriIq] and allow the
 * starting and stopping of the services Jibri provides.
 * [XmppApi] subscribes to [JibriStatusManager] status updates and translates those into
 * XMPP presence (defined by [JibriStatusPacketExt]) updates to advertise the status of this Jibri.
 * XMPP presence (defined by [JibriStatusPacketExt]) updates to advertise the status of this Jibri.
 * [XmppApi] takes care of translating the XMPP commands into the appropriate
 * [JibriManager] API calls and translates the results into XMPP IQ responses.
 */
class XmppApi(
    private val jibriManager: JibriManager,
    private val xmppConfigs: List<XmppEnvironmentConfig>,
    private val jibriStatusManager: JibriStatusManager,
    private val statsDClient: JibriStatsDClient? = null
) : IQListener {
    private val logger = createLogger()

    private val connectionStateListener = object : ConnectionStateListener {
        override fun connected(mucClient: MucClient) {
            statsDClient?.incrementCounter(XMPP_CONNECTED, mucClient.tags())
        }
        override fun reconnecting(mucClient: MucClient) {
            statsDClient?.incrementCounter(XMPP_RECONNECTING, mucClient.tags())
        }
        override fun reconnectionFailed(mucClient: MucClient) {
            statsDClient?.incrementCounter(XMPP_RECONNECTION_FAILED, mucClient.tags())
        }
        override fun pingFailed(mucClient: MucClient) {
            statsDClient?.incrementCounter(XMPP_PING_FAILED, mucClient.tags())
        }

        /**
         * If XMPP disconnects we lost our communication channel with jicofo. Jicofo currently doesn't attempt to
         * communicate later, and starts a new session (with a different jibri) immediately. Make sure in this case the
         * recording is stopped.
         */
        override fun closed(mucClient: MucClient) {
            statsDClient?.incrementCounter(XMPP_CLOSED, mucClient.tags())
            maybeStop(mucClient)
        }

        /**
         * If XMPP disconnects we lost our communication channel with jicofo. Jicofo currently doesn't attempt to
         * communicate later, and starts a new session (with a different jibri) immediately. Make sure in this case the
         * recording is stopped.
         */
        override fun closedOnError(mucClient: MucClient) {
            statsDClient?.incrementCounter(XMPP_CLOSED_ON_ERROR, mucClient.tags())
            maybeStop(mucClient)
        }

        private fun maybeStop(mucClient: MucClient) {
            val xmppEnvironment = getXmppEnvironment(mucClient) ?: return
            val environmentContext = createEnvironmentContext(xmppEnvironment, mucClient)
            if (jibriManager.currentEnvironmentContext == environmentContext) {
                logger.warn("XMPP disconnected, stopping.")
                statsDClient?.incrementCounter(STOPPED_ON_XMPP_CLOSED, mucClient.tags())
                jibriManager.stopService()
            }
        }

        /** Create statsd tags for a [MucClient]. */
        private fun MucClient.tags() = "xmpp_server_host:$id"
    }
    private lateinit var mucClientManager: MucClientManager

    val recordingsDirectory: String by config {
        "JibriConfig::recordingDirectory" { Config.legacyConfigSource.recordingDirectory!! }
        "jibri.recording.recordings-directory".from(Config.configSource)
    }

    fun updatePresenceTask(){
        // 创建一个Timer对象
        val timer = Timer()

        // 获取当前时间
        val currentTime = Calendar.getInstance()

        // 设置明天凌晨0点的时间
        val tomorrowZeroHour = Calendar.getInstance()
        tomorrowZeroHour.set(Calendar.HOUR_OF_DAY, 0)
        tomorrowZeroHour.set(Calendar.MINUTE, 0)
        tomorrowZeroHour.set(Calendar.SECOND, 0)
        tomorrowZeroHour.set(Calendar.MILLISECOND, 0)
        tomorrowZeroHour.add(Calendar.DAY_OF_YEAR, 1)

        // 计算从当前时间到明天凌晨的时间间隔
        val delay = tomorrowZeroHour.timeInMillis - currentTime.timeInMillis

        // 创建一个TimerTask对象，用于执行你的任务代码
        val task = object : TimerTask() {
            override fun run() {
                // 在这里编写你的任务代码
                logger.info { "execute task ............." }
                updatePresence(jibriStatusManager.overallStatus)
            }
        }

        // 将任务安排在明天凌晨0点执行
        timer.schedule(task, delay, 24 * 60 * 60 * 1000)
    }

    /**
     * Start up the XMPP API by connecting and logging in to all the configured XMPP environments.  For each XMPP
     * connection, we'll listen for incoming [JibriIq] messages and handle them appropriately.  Join the MUC on
     * each connection and send an initial [JibriStatusPacketExt] presence.
     */
    fun start(mucManager: MucClientManager = MucClientManager()) {
        this.mucClientManager = mucManager

        PingManager.setDefaultPingInterval(30)
        JibriStatusPacketExt.registerExtensionProvider()
        ProviderManager.addIQProvider(
            JibriIq.ELEMENT, JibriIq.NAMESPACE, JibriIqProvider()
        )
        updatePresence(jibriStatusManager.overallStatus)
        jibriStatusManager.addStatusHandler(::updatePresence)

        mucClientManager.registerIQ(JibriIq())
        mucClientManager.setIQListener(this)
        mucClientManager.addConnectionStateListener(connectionStateListener)

        // Join all the MUCs we've been told to
        for (config in xmppConfigs) {
            for (host in config.xmppServerHosts) {
                logger.info("Connecting to xmpp environment on $host with config $config")
                val hostDetails: List<String> = host.split(":")

                // We need to use the host as the ID because we'll only get one MUC client per 'ID' and
                // we may have multiple hosts for the same environment
                val clientConfig = MucClientConfiguration(host).apply {
                    hostname = hostDetails[0]
                    domain = config.controlLogin.domain
                    username = config.controlLogin.username
                    password = config.controlLogin.password

                    if (hostDetails.size > 1) {
                        port = hostDetails[1]
                    } else {
                        config.controlLogin.port?.let { port = it.toString() }
                    }

                    if (config.trustAllXmppCerts) {
                        logger.info(
                            "The trustAllXmppCerts config is enabled for this domain, " +
                                "all XMPP server provided certificates will be accepted"
                        )
                        disableCertificateVerification = config.trustAllXmppCerts
                    }

                    if (config.securityMode == ConnectionConfiguration.SecurityMode.disabled) {
                        logger.info(
                            "XMPP security is disabled for this domain, no TLS will be used."
                        )
                    }
                    securityMode = config.securityMode

                    val recordingMucJid =
                        JidCreate.bareFrom("${config.controlMuc.roomName}@${config.controlMuc.domain}").toString()
                    val sipMucJid: String? = config.sipControlMuc?.let {
                        JidCreate.entityBareFrom(
                            "${config.sipControlMuc.roomName}@${config.sipControlMuc.domain}"
                        ).toString()
                    }
                    mucJids = listOfNotNull(recordingMucJid, sipMucJid)
                    mucNickname = config.controlMuc.nickname
                }

                mucClientManager.addMucClient(clientConfig)
            }
        }
    }

    /**
     * Function to update outgoing [presence] stanza with jibri status.
     */
    private fun updatePresence(jibriStatus: JibriStatus) {
        if (jibriStatus.shouldBeSentToMuc()) {
            logger.info("Jibri reports its status is now $jibriStatus, publishing presence to connections")
            val ext = jibriStatus.toJibriStatusExt()
            ext.setChildExtension(JibriNodeExtension().apply {
                ip = MainConfig.innerIp
                status = jibriStatus.busyStatus.name
                name = MainConfig.containerName
                id = MainConfig.jibriId
            })
            mucClientManager.setPresenceExtension(ext)
        } else {
            logger.info("Not forwarding status $jibriStatus to the MUC")
        }
    }

    /**
     * Handles the JibriIQ.
     *
     * @param iq the IQ to be handled.
     * @param mucClient the [MucClient] from which the IQ comes.
     * @return the IQ to be sent as a response or `null`.
     */
    override fun handleIq(iq: IQ, mucClient: MucClient): IQ {
        return if (iq is JibriIq) {
            handleJibriIq(iq, mucClient)
        } else {
            IQ.createErrorResponse(iq, StanzaError.getBuilder().setCondition(StanzaError.Condition.bad_request).build())
        }
    }

    /**
     * Helper function to handle a [JibriIq] message with the context of the [XmppEnvironmentConfig] and [MucClient]
     * that this [JibriIq] was received on.
     */
    private fun handleJibriIq(jibriIq: JibriIq, mucClient: MucClient): IQ {
        logger.info("Received JibriIq ${jibriIq.toXML()} from environment $mucClient")
        val xmppEnvironment = getXmppEnvironment(mucClient)
            ?: return IQ.createErrorResponse(
                jibriIq,
                StanzaError.getBuilder().setCondition(StanzaError.Condition.bad_request).build()
            )
        return when (jibriIq.action) {
            JibriIq.Action.START -> handleStartJibriIq(jibriIq, xmppEnvironment, mucClient)
            JibriIq.Action.STOP -> handleStopJibriIq(jibriIq)
            else -> IQ.createErrorResponse(
                jibriIq,
                StanzaError.getBuilder().setCondition(StanzaError.Condition.bad_request).build()
            )
        }
    }

    private fun getXmppEnvironment(mucClient: MucClient) = xmppConfigs.find {
        it.xmppServerHosts.contains(mucClient.id)
    }

    /**
     * Handle a start [JibriIq] message.  We'll respond immediately with a [JibriIq.Status.PENDING] IQ response and
     * send a new IQ with the subsequent stats after starting the service:
     * [JibriIq.Status.OFF] if there was an error starting the service (or an error while the service was running).
     *  In this case, a [JibriIq.FailureReason] will be set as well.
     * [JibriIq.Status.ON] if the service started successfully
     */
    private fun handleStartJibriIq(
        startJibriIq: JibriIq,
        xmppEnvironment: XmppEnvironmentConfig,
        mucClient: MucClient
    ): IQ {
        logger.info("Received start request, starting service")
        // If there is an issue with the service while it's running, we need to send an IQ
        // to notify the caller who invoked the service of its status, so we'll listen
        // for the service's status while it's running and this method will be invoked
        // if it changes
        val serviceStatusHandler = createServiceStatusHandler(startJibriIq, mucClient)
        return try {
            handleStartService(
                startJibriIq,
                xmppEnvironment,
                createEnvironmentContext(xmppEnvironment, mucClient),
                serviceStatusHandler,
                mucClient
            )
            logger.info("Sending 'pending' response to start IQ")
            startJibriIq.createResult {
                status = JibriIq.Status.PENDING
            }
        } catch (busy: JibriBusyException) {
            logger.error("Jibri is currently busy, cannot service this request")
            startJibriIq.createResult {
                status = JibriIq.Status.OFF
                failureReason = JibriIq.FailureReason.BUSY
                shouldRetry = true
            }
        } catch (iq: UnsupportedIqMode) {
            logger.error("Unsupported IQ mode: ${iq.iqMode}")
            startJibriIq.createResult {
                status = JibriIq.Status.OFF
                failureReason = JibriIq.FailureReason.ERROR
                shouldRetry = false
            }
        } catch (t: Throwable) {
            logger.error("Error starting Jibri service ", t)
            startJibriIq.createResult {
                status = JibriIq.Status.OFF
                failureReason = JibriIq.FailureReason.ERROR
                shouldRetry = true
            }
        }
    }

    private fun createServiceStatusHandler(request: JibriIq, mucClient: MucClient): JibriServiceStatusHandler {
        return { serviceState ->
            when (serviceState) {
                is ComponentState.Error -> {
                    with(JibriIqHelper.create(request.from, status = JibriIq.Status.OFF)) {
                        failureReason = JibriIq.FailureReason.ERROR
                        sipAddress = request.sipAddress
                        shouldRetry = serviceState.error.shouldRetry()
                        logger.info(
                            "Current service had an error ${serviceState.error}, " +
                                "sending error iq ${toXML()}"
                        )
                        mucClient.sendStanza(this)
                        sendRecordingIq(request, mucClient)
                    }
                }
                is ComponentState.Finished -> {
                    with(JibriIqHelper.create(request.from, status = JibriIq.Status.OFF)) {
                        sipAddress = request.sipAddress
                        logger.info("Current service finished, sending off iq ${toXML()}")
                        mucClient.sendStanza(this)
                        sendRecordingIq(request, mucClient)
                    }
                }
                is ComponentState.Running -> {
                    with(JibriIqHelper.create(request.from, status = JibriIq.Status.ON)) {
                        sipAddress = request.sipAddress
                        logger.info("Current service started up successfully, sending on iq ${toXML()}")
                        mucClient.sendStanza(this)
                        mucClient.sendStanza(RecordExtendIq().apply {
                            startTime = System.currentTimeMillis().toString()
                            roomName = request.room.localpart.toString()
                            to = request.from
                        })
                    }
                }
                else -> {
                    logger.info("XmppAPI ignoring service state update: $serviceState")
                }
            }
        }
    }

    private fun sendRecordingIq(request: JibriIq, mucClient: MucClient) {
        val sessionId = request.sessionId
        val dir = File("$recordingsDirectory/$sessionId")
        if (dir.exists() && dir.isDirectory && Objects.nonNull(dir.listFiles())) {
            val recordFileName = dir.listFiles().findFirstMp4FileName()
            if (recordFileName != null) {
                mucClient.sendStanza(RecordingIq().apply {
                    fileName = recordFileName
                    filePath = "$sessionId/"
                    suffix = "mp4"
                    size = Files.size(Path("$recordingsDirectory/$sessionId/$fileName")).toString()
                    roomName = request.room.localpart.toString()
                    to = request.from
                    logger.info("send recordFileInfo -> ${this.toXML()}")
                })
                TaskPools.ioPool.submit {
                    uploadRecordFile(dir, sessionId, recordFileName)
                }
            }
        }
    }

    private fun uploadRecordFile(dir: File, sessionId: String, fileName: String) {
        var inputStream: FileInputStream? = null
        try {
            val minioClient = MinioClientConfig().getClient()
            val path = "${dir.path}/$fileName"
            val file = File(path)
            inputStream = file.inputStream()
            logger.info("filePath -> $path, file size -> ${file.length()}")
            minioClient.putObject(
                    PutObjectArgs.builder().
                    stream(inputStream, file.length(), -1)
                    .bucket(MinioClientConfig.BUCKET).
                    `object`("${MinioClientConfig.STORE}/$sessionId/$fileName").
                    build())
            logger.info("Minio upload file, file size ${dir.length()}, file object name -> ${MinioClientConfig.STORE}/$sessionId/$fileName")
        } catch (e: Exception) {
            logger.error("Minio upload file error -> $e")
            e.printStackTrace()
        } finally {
            inputStream?.close()
        }
    }

    private fun Array<File>?.findFirstMp4FileName(): String? {
        return this?.find { it.isFile && it.name.endsWith(".mp4") }?.name
    }

    /**
     * Handle a stop [JibriIq] message to stop the currently running service (if there is one).  Send a [JibriIq]
     * response with [JibriIq.Status.OFF].
     */
    private fun handleStopJibriIq(stopJibriIq: JibriIq): IQ {
        jibriManager.stopService()
        // By this point the service has been fully stopped
        return stopJibriIq.createResult {
            status = JibriIq.Status.OFF
        }
    }

    /**
     * Helper function to actually start the service.  We need to parse the fields in the [JibriIq] message
     * to determine which [JibriManager] service API to call, as well as convert the types into what [JibriManager]
     * expects
     */
    private fun handleStartService(
        startIq: JibriIq,
        xmppEnvironment: XmppEnvironmentConfig,
        environmentContext: EnvironmentContext,
        serviceStatusHandler: JibriServiceStatusHandler,
        mucClient: MucClient
    ) {
        val callUrlInfo = getCallUrlInfoFromJid(
            startIq.room,
            xmppEnvironment.stripFromRoomDomain,
            xmppEnvironment.xmppDomain,
            xmppEnvironment.baseUrl,
            mucClient
        )
        val appData = startIq.appData?.let {
            jacksonObjectMapper().readValue<AppData>(startIq.appData)
        }
        val serviceParams = ServiceParams(xmppEnvironment.usageTimeoutMins, appData)
        val callParams = CallParams(callUrlInfo)
        logger.info("Parsed call url info: $callUrlInfo")

        when (startIq.mode()) {
            JibriMode.FILE -> {
                jibriManager.startFileRecording(
                    serviceParams,
                    FileRecordingRequestParams(callParams, startIq.sessionId, xmppEnvironment.callLogin),
                    environmentContext,
                    serviceStatusHandler
                )
            }
            JibriMode.STREAM -> {
                val rtmpUrl = if (startIq.streamId.isRtmpUrl()) {
                    startIq.streamId
                } else {
                    "$YOUTUBE_URL/${startIq.streamId}"
                }
                val viewingUrl = if (startIq.youtubeBroadcastId != null) {
                    if (startIq.youtubeBroadcastId.isViewingUrl()) {
                        startIq.youtubeBroadcastId
                    } else {
                        "http://youtu.be/${startIq.youtubeBroadcastId}"
                    }
                } else {
                    null
                }
                logger.info("Using RTMP URL $rtmpUrl and viewing URL $viewingUrl")
                jibriManager.startStreaming(
                    serviceParams,
                    StreamingParams(
                        callParams,
                        startIq.sessionId,
                        xmppEnvironment.callLogin,
                        rtmpUrl = rtmpUrl,
                        viewingUrl = viewingUrl
                    ),
                    environmentContext,
                    serviceStatusHandler
                )
            }
            JibriMode.SIPGW -> {
                jibriManager.startSipGateway(
                    serviceParams,
                    SipGatewayServiceParams(
                        callParams,
                        xmppEnvironment.callLogin,
                        SipClientParams(startIq.sipAddress, startIq.displayName)
                    ),
                    environmentContext,
                    serviceStatusHandler
                )
            }
            else -> {
                throw UnsupportedIqMode(startIq.mode().toString())
            }
        }
    }
}

private fun String.isRtmpUrl(): Boolean =
    startsWith("rtmp://", ignoreCase = true) || startsWith("rtmps://", ignoreCase = true)
private fun String.isViewingUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

private fun createEnvironmentContext(xmppEnvironment: XmppEnvironmentConfig, mucClient: MucClient) =
    EnvironmentContext("${xmppEnvironment.name}-${mucClient.id}")
