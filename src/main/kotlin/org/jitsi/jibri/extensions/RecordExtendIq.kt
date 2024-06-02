package org.jitsi.jibri.extensions

import org.jivesoftware.smack.packet.IQ

/**
 * @Author dzh
 * @Create 2023/12/12 16:09
 * @Package org.jitsi.jicofo.extensions.gmeet
 * @Description: Copyright (c) Company:Genew Technologies Co., Ltd. 2005-2020
 */
class RecordExtendIq : IQ {
    var startTime: String? = null
    var roomName: String? = null

    constructor() : super(ELEMENT, NAMESPACE)
    constructor(startTime: String?, roomName: String?) : super(ELEMENT, NAMESPACE) {
        this.startTime = startTime
        this.roomName = roomName
    }

    override fun getIQChildElementBuilder(xml: IQChildElementXmlStringBuilder): IQChildElementXmlStringBuilder {
        if (startTime != null) {
            xml.attribute("startTime", startTime)
        }
        if (roomName != null) {
            xml.attribute("roomName", roomName)
        }
        xml.setEmptyElement()
        return xml
    }

    companion object {
        const val ELEMENT = "record_extend"
        const val NAMESPACE = "http://gmeet/protocol/record_extend"
    }
}
