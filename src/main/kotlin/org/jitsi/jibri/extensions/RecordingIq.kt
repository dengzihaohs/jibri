package org.jitsi.jibri.extensions

import org.jivesoftware.smack.packet.IQ

/**
 * @Author dzh
 * @Create 2023/7/26 16:33
 * @Package org.jitsi.jicofo.extensions.gmeet
 * @Description: Copyright (c) Company:Genew Technologies Co., Ltd. 2005-2020
 */
class RecordingIq : IQ {
    var filePath: String? = null
    var size: String? = null
    var suffix: String? = null
    var fileName: String? = null
    var roomName: String? = null

    constructor() : super(ELEMENT, NAMESPACE) {}
    constructor(fileName: String?, filePath: String?, suffix: String?, size: String?, roomName: String?) : super(ELEMENT, NAMESPACE) {
        this.size = size
        this.suffix = suffix
        this.filePath = filePath
        this.fileName = fileName
        this.roomName = roomName
    }

    override fun getIQChildElementBuilder(xml: IQChildElementXmlStringBuilder): IQChildElementXmlStringBuilder {
        if (fileName != null) {
            xml.attribute("fileName", fileName)
        }
        if (filePath != null) {
            xml.attribute("filePath", filePath)
        }
        if (size != null) {
            xml.attribute("size", size)
        }
        if (suffix != null) {
            xml.attribute("suffix", suffix)
        }
        if (roomName != null) {
            xml.attribute("roomName", roomName)
        }
        xml.setEmptyElement()
        return xml
    }

    companion object {
        const val ELEMENT = "recording"
        const val NAMESPACE = "http://gmeet/protocol/recording"
    }
}