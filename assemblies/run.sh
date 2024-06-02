#!/bin/bash

HOCON_CONFIG="/var/jibri.conf"
if [ ! -f $HOCON_CONFIG ]; then
  echo "Generating an empty jibri.conf file"
  echo "jibri {

   gmeet-port=\"${GMEET_ENV_TCP_PORT}\"

   id=\"${JIBRI_INSTANCE_ID}\"

   container-name=\"${CONTAINER_NAME}\"

   inner-ip=\"${INNER_IP}\"

   // Whether or not Jibri should return to idle state after handling
   // (successfully or unsuccessfully) a request.  A value of 'true'
   // here means that a Jibri will NOT return back to the IDLE state
   // and will need to be restarted in order to be used again.
   single-use-mode = false
     api {
       http {
         external-api-port = 2222
         internal-api-port = 3333
       }
       xmpp {
         // See example_xmpp_envs.conf for an example of what is expected here
         environments = [
             {
                 // A user-friendly name for this environment
                 name = \"${XMPP_ENV_NAME}\"

                 // A list of XMPP server hosts to which we'll connect
                 xmpp-server-hosts = [
                     \"${XMPP_SERVER}\"
                 ]

                 // The base XMPP domain
                 xmpp-domain = \"${XMPP_DOMAIN}\"

                 // The MUC we'll join to announce our presence for
                 // recording and streaming services
                 control-muc {
                     domain = \"${XMPP_INTERNAL_MUC_DOMAIN}\"
                     room-name = \"${JIBRI_BREWERY_MUC}\"
                     nickname = \"${JIBRI_INSTANCE_ID}\"
                 }

                 // The login information for the control MUC
                 control-login {
                     domain = \"${XMPP_AUTH_DOMAIN}\"
                     username = \"${JIBRI_XMPP_USER}\"
                     password = \"${JIBRI_XMPP_PASSWORD}\"
                     port = ${JIBRI_XMPP_PORT}
                 }

                 sip-control-muc {
                     domain = \"${XMPP_INTERNAL_MUC_DOMAIN}\"
                     room-name = \"${JIBRI_BREWERY_MUC_SIP}\"
                     nickname = \"${JIBRI_INSTANCE_ID}\"
                 }

                 // The login information the selenium web client will use
                 call-login {
                     domain = \"${XMPP_RECORDER_DOMAIN}\"
                     username = \"${JIBRI_RECORDER_USER}\"
                     password = \"${JIBRI_RECORDER_PASSWORD}\"
                 }

                 // The value we'll strip from the room JID domain to derive
                 // the call URL
                 strip-from-room-domain = \"${JIBRI_STRIP_DOMAIN_JID}.\"

                 // How long Jibri sessions will be allowed to last before
                 // they are stopped.  A value of 0 allows them to go on
                 // indefinitely
                 usage-timeout = \"0\"

                 // Whether or not we'll automatically trust any cert on
                 // this XMPP domain
                 trust-all-xmpp-certs = true
             }
         ]
       }
     }
     recording {
     }

     ffmpeg {
     }
     minio {
     }
     chrome {
       // The flags which will be passed to chromium when launching
       flags = [
         \"--use-fake-ui-for-media-stream\",
         \"--start-maximized\",
         \"--kiosk\",
         \"--enabled\",
         \"--autoplay-policy=no-user-gesture-required\",
         \"--ignore-certificate-errors\",
         \"--disable-dev-shm-usage\"
       ]
     }

     call-status-checks {
     // If all clients have their audio and video muted and if Jibri does not
     // detect any data stream (audio or video) comming in, it will stop
     // recording after NO_MEDIA_TIMEOUT expires.
     no-media-timeout = 60 seconds

     // If all clients have their audio and video muted, Jibri consideres this
     // as an empty call and stops the recording after ALL_MUTED_TIMEOUT expires.
     all-muted-timeout = 10 minutes

     // When detecting if a call is empty, Jibri takes into consideration for how
     // long the call has been empty already. If it has been empty for more than
     // DEFAULT_CALL_EMPTY_TIMEOUT, it will consider it empty and stop the recording.
     default-call-empty-timeout = 60 seconds
   }
 }" >> $HOCON_CONFIG

hocon -f $HOCON_CONFIG set "jibri.ffmpeg.resolution" "\"1280x720\""
hocon -f $HOCON_CONFIG set "jibri.ffmpeg.audio-source" "\"${JIBRI_FFMPEG_AUDIO_SOURCE}\""
hocon -f $HOCON_CONFIG set "jibri.ffmpeg.audio-device" "\"${JIBRI_FFMPEG_AUDIO_DEVICE}\""
hocon -f $HOCON_CONFIG set "jibri.recording.recordings-directory" "\"${JIBRI_RECORDING_DIR}\""
hocon -f $HOCON_CONFIG set "jibri.recording.finalize-script" "\"${JIBRI_FINALIZE_RECORDING_SCRIPT_PATH}\""
hocon -f $HOCON_CONFIG set "jibri.minio.url" "\"${GMEET_ENV_NUMAX_MINIO_URL}\""
hocon -f $HOCON_CONFIG set "jibri.minio.access-key" "\"numax\""
hocon -f $HOCON_CONFIG set "jibri.minio.secret-key" "\"Nucleus!\""
fi

cp /var/jibri.conf /etc/jitsi/jibri/jibri.conf
ln -sf /etc/jitsi/jibri/jibri.conf /var/jibri.conf

# Docker容器后台运行,就必须有一个前台进程
dummy=/dummy
if [ ! -f "$dummy" ]; then
	touch $dummy
fi
tail -f $dummy
