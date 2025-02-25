/*
 * Copyright @ 2018 Atlassian Pty Ltd
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

package org.jitsi.jibri.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jitsi.jibri.logger
import org.jivesoftware.smack.ConnectionConfiguration
import java.io.File

data class XmppCredentials(
    val domain: String = "",
    val port: Int? = null,
    val username: String = "",
    val password: String = ""
) {
    override fun toString(): String {
        return "XmppCredentials(domain=$domain, port=$port, username=$username, password=*****)"
    }
}

fun com.typesafe.config.Config.toXmppCredentials(): XmppCredentials =
    XmppCredentials(
        domain = getString("domain"),
        port = if (hasPath("port")) getInt("port") else null,
        username = getString("username"),
        password = getString("password")
    )

data class XmppMuc(
    val domain: String,
    @JsonProperty("room_name")
    val roomName: String,
    val nickname: String
)

fun com.typesafe.config.Config.toXmppMuc(): XmppMuc =
    XmppMuc(
        domain = getString("domain"),
        roomName = getString("room-name"),
        nickname = getString("nickname")
    )

data class XmppEnvironmentConfig(
    /**
     * A user-friendly name for this environment
     */
    val name: String,
    /**
     * A list of xmpp server hosts to which we'll connect
     */
    @JsonProperty("xmpp_server_hosts")
    val xmppServerHosts: List<String>,
    /**
     * The base xmpp domain
     */
    @JsonProperty("xmpp_domain")
    val xmppDomain: String,
    /**
     * The baseUrl we'll use, if set, to join the call
     */
    @JsonProperty("baseUrl")
    val baseUrl: String?,
    /**
     * The login information for the control API
     */
    @JsonProperty("control_login")
    val controlLogin: XmppCredentials,
    /**
     * The muc we'll join to announce our presence for
     * recording and streaming services
     */
    @JsonProperty("control_muc")
    val controlMuc: XmppMuc,
    /**
     * The muc we'll join to announce our presence
     * for sip gateway services
     * TODO: should this and controlMuc above be
     * optional?  but somehow require at least one
     * to be set?
     */
    @JsonProperty("sip_control_muc")
    val sipControlMuc: XmppMuc?,
    /**
     * The login information the selenium web client will use
     */
    @JsonProperty("call_login")
    val callLogin: XmppCredentials,
    /**
     * The value we'll strip from the room jid domain to derive
     * the call url
     */
    @JsonProperty("room_jid_domain_string_to_strip_from_start")
    val stripFromRoomDomain: String,
    /**
     * How long Jibri sessions will be allowed to last before
     * they are stopped.  A value of 0 allows them to go on
     * indefinitely
     */
    @JsonProperty("usage_timeout")
    val usageTimeoutMins: Int,
    /**
     * Whether or not we'll automatically trust any
     * cert on this XMPP domain
     */
    @JsonProperty("always_trust_certs")
    val trustAllXmppCerts: Boolean = true,
    /**
     * The XMPP security mode to use for the XMPP connection
     */
    @JsonProperty("security_mode")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val securityMode: ConnectionConfiguration.SecurityMode? = null
)

fun com.typesafe.config.Config.toXmppEnvironment(): XmppEnvironmentConfig =
    XmppEnvironmentConfig(
        name = getString("name"),
        xmppServerHosts = getStringList("xmpp-server-hosts"),
        xmppDomain = getString("xmpp-domain"),
        baseUrl = if (hasPath("base-url")) {
            getString("base-url")
        } else null,
        controlLogin = getConfig("control-login").toXmppCredentials(),
        controlMuc = getConfig("control-muc").toXmppMuc(),
        sipControlMuc = if (hasPath("sip-control-muc")) {
            getConfig("sip-control-muc").toXmppMuc()
        } else null,
        callLogin = getConfig("call-login").toXmppCredentials(),
        stripFromRoomDomain = getString("strip-from-room-domain"),
        usageTimeoutMins = getDuration("usage-timeout").toMinutes().toInt(),
        trustAllXmppCerts = getBoolean("trust-all-xmpp-certs"),
        securityMode = if (hasPath("security-mode")) {
            getEnum(ConnectionConfiguration.SecurityMode::class.java, "security-mode")
        } else null
    )

data class JibriConfig(
    @JsonProperty("jibri_id")
    val jibriId: String? = null,
    @JsonProperty("recording_directory")
    val recordingDirectory: String? = null,
    /**
     * Whether or not Jibri should return to idle state
     * after handling (successfully or unsuccessfully)
     * a request.  A value of 'true' here means that a Jibri
     * will NOT return back to the IDLE state and will need
     * to be restarted in order to be used again.
     */
    @JsonProperty("single_use_mode")
    val singleUseMode: Boolean? = null,

    @JsonProperty("gmeet_port")
    val gmeetPort: String? = null,
    @JsonProperty("minio_url")
    val minioUrl: String ?= null,
    @JsonProperty("minio_access_key")
    val minioAccessKey: String? = null,
    @JsonProperty("minio_secret_Key")
    val minioSecretKey: String? = null,

    @JsonProperty("container_name")
    val containerName: String? = null,
    @JsonProperty("inner_ip")
    val innerIp: String? = null,
    /**
     * Whether or not pushing stats to statsd
     * should be enabled.  See [org.jitsi.jibri.statsd.JibriStatsDClient].
     */
    @JsonProperty("enable_stats_d")
    val enabledStatsD: Boolean? = null,
    @JsonProperty("finalize_recording_script_path")
    val finalizeRecordingScriptPath: String? = null,
    @JsonProperty("xmpp_environments")
    val xmppEnvironments: List<XmppEnvironmentConfig>? = null
)

fun loadConfigFromFile(configFile: File): JibriConfig? {
    return try {
        val config: JibriConfig = jacksonObjectMapper()
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            .readValue(configFile)
        logger.info("Parsed legacy config:\n$config")
        config
    } catch (e: MissingKotlinParameterException) {
        logger.error("A required config parameter was missing: ${e.originalMessage}")
        null
    } catch (e: UnrecognizedPropertyException) {
        logger.error("An unrecognized config parameter was found: ${e.originalMessage}")
        null
    } catch (e: InvalidFormatException) {
        logger.error("A config parameter was incorrectly formatted: ${e.localizedMessage}")
        null
    }
}
